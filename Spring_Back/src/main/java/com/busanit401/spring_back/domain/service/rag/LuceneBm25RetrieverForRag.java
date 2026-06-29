package com.busanit401.spring_back.domain.service.rag;

import com.busanit401.spring_back.dto.ChatBotFaqDocDTO;
import com.busanit401.spring_back.dto.ChatBotScoredDocDTO;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.analysis.ko.KoreanPartOfSpeechStopFilter;
import org.apache.lucene.analysis.ko.KoreanTokenizer.DecompoundMode;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * LuceneBm25RetrieverForRag는 생성시 생성자로 db에서 읽어온 id와 [질문 답변]의 문자열에서 토큰화한 단어들(a)의 색인과 검색기를 생성
 * searchScored는 질의 요청이 왔을때 질의 문자열에서 토큰화하여 중복없이 추출한 단어들(b)을
 * a와 비교하여 겹치는 단어가 있는 것들을 검색하여 BM25 점수를 측정하고, BM25 점수 상위 k개의 문서를 추출하여 db에서 읽어온 id와 BM25점수를 반환
 */
public class LuceneBm25RetrieverForRag implements ScoringRetrieverForRag, AutoCloseable {

    public static final double DEFAULT_K1 = 1.2;    //tf(빈도)계수 표준 기본값   //상한에 수렴(곡선)
    public static final double DEFAULT_B = 0.75;    //문서길이계수 표준 기본값 //평균대비 보정

    private static final String FIELD_ID = "id";
    private static final String FIELD_TEXT = "text";

    private final String name;
    private final Analyzer analyzer;
    private final Directory directory;
    private final DirectoryReader reader;
    private final IndexSearcher searcher;

    /** 기본 설정: NoriTokenizer와 동일한 KoreanAnalyzer + BM25(1.2, 0.75). */
    public LuceneBm25RetrieverForRag(String name, List<ChatBotFaqDocDTO> corpus) {
        this(name, corpus, defaultAnalyzer(), DEFAULT_K1, DEFAULT_B);
    }

    /**
     * @param analyzer 색인·질의에 함께 쓸 Analyzer (보통 {@link KoreanAnalyzer})
     * @param k1       BM25 TF 포화 파라미터
     * @param b        BM25 문서길이 정규화 파라미터
     */
    public LuceneBm25RetrieverForRag(String name, List<ChatBotFaqDocDTO> corpus, Analyzer analyzer, double k1, double b) {
        this.name = name;
        this.analyzer = analyzer;
        this.directory = new ByteBuffersDirectory();    //휘발성 폴더
        try {
            // 1) 색인 생성 — Analyzer로 토큰화, Similarity를 BM25로 지정
            IndexWriterConfig cfg = new IndexWriterConfig(analyzer);    //색인 설정
            cfg.setSimilarity(new BM25Similarity((float) k1, (float) b));
            try (IndexWriter writer = new IndexWriter(directory, cfg)) {    //색인 생성(설정)
                for (ChatBotFaqDocDTO doc : corpus) {
                    Document d = new Document();    //Lucene의 색인 단위
                    d.add(new StringField(FIELD_ID, doc.getId(), Field.Store.YES));  // 저장 + 색인(분석 X)(검색 결과로 돌려줄 id)
                    d.add(new TextField(FIELD_TEXT, doc.searchableText(), Field.Store.NO)); // Analyzer가 토큰화(빠르게 검색할 대상)("질문 답변"형식의 문자열에서 단어를 추출)해서 역색인을 만듦(정방향 색인 -> 문서에서 단어 찾음, 역방향 색인 -> 단어가 있는 문서를 찾음)
                    writer.addDocument(d);

                }
                //이제 문서는 자체 id와 내가 색인으로 저장했던 id(반환받을수 있는 값(분석 안함)와 쪼개진 단어들(반환x, 분석만가능)가 포함되어있음
            }
            // 2) 검색기 — 같은 BM25 Similarity로 점수 매김
            this.reader = DirectoryReader.open(directory);  //IndexWriter로 만든 휘발성 폴더 열고
            this.searcher = new IndexSearcher(reader);  //추가했던 문서 인덱스 검색자
            this.searcher.setSimilarity(new BM25Similarity((float) k1, (float) b)); //점수를 매길 계수 지정
        } catch (IOException e) {
            throw new UncheckedIOException("Lucene BM25 색인 생성 실패", e);
        }
    }

    /** NoriTokenizer와 동일: 복합명사 MIXED 분해 + Nori 기본 불용품사 제거. */
    /**
     * 사용자 사전 null,
     * 복합명사 분해 모드(NONE/DISCARD/MIXED)(분해전과 분해 후의 단어 모두 사용),
     * 제거할 품사 태그 집합,
     * 미등록어를 한 글자씩 쪼개 출력할지
     */
    private static Analyzer defaultAnalyzer() {
        return new KoreanAnalyzer(null, DecompoundMode.MIXED,
                KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS, false);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<ChatBotScoredDocDTO> searchScored(String query, int k) {
        // 질의를 색인과 동일한 Analyzer로 토큰화 → 각 토큰을 OR(SHOULD)로 묶은 BooleanQuery.
        Set<String> terms = analyze(query); //질문의 토큰들을 중복 제거해서 읽어옴
        if (terms.isEmpty()) {
            return List.of();
        }
        /**
         * new Term(FIELD_TEXT, t)
         *   //  = "text 필드의 단어 t"   예: Term("text", "환불") = "text에 있는 '환불'"
         *   Term = (필드, 단어) 한 쌍. "어느 필드의 어떤 단어"를 가리키는 가장 작은 단위.
         *
         *   new TermQuery(new Term(FIELD_TEXT, t))
         *   //  = "text에 '환불'이 든 문서를 찾아라"  (단어 하나짜리 검색)
         *   TermQuery = "이 단어 든 문서 찾기" 쿼리. 단어 1개 검색.
         *
         *   BooleanClause.Occur.SHOULD
         *   //  = OR 조건 (있으면 좋음, 필수 아님)
         *   Occur.SHOULD = OR. (MUST=AND, MUST_NOT=NOT). SHOULD를 여러 개 = "이것들 중 아무거나 매칭".
         *
         *   BooleanQuery.Builder qb = new BooleanQuery.Builder();
         *   //  = 여러 쿼리를 묶을 "조립기" 시작
         *   BooleanQuery = 여러 쿼리를 AND/OR로 묶은 쿼리. Builder 는 그걸 조립하는 도구.
         *
         *    질의 {환불, 정책, 안내} 면:
         *   시작:        qb = (빈 쿼리)
         *   환불 add:    qb = "환불"
         *   정책 add:    qb = "환불 OR 정책"
         *   안내 add:    qb = "환불 OR 정책 OR 안내"
         *   build():     Query = "환불 OR 정책 OR 안내"  ← 완성
         */
        BooleanQuery.Builder qb = new BooleanQuery.Builder();   //쿼리 조립
        for (String t : terms) {    //추려낸 단어를 for문을 돌면서 or로 붙여서 쿼리로 만듦
            qb.add(new TermQuery(new Term(FIELD_TEXT, t)), BooleanClause.Occur.SHOULD);
            //new Term(FIELD_TEXT, t) : 찾을 필드(검색용 필드), 단어
            //new TermQuery(new Term(FIELD_TEXT, t) : 찾는 행위
            //qb.add : 쿼리 조립
            //BooleanClause.Occur.SHOULD : or을 붙임
        }
        Query q = qb.build();   //쿼리 완성

        try {
            //현재 TopDocs는 생성한 내부 문서의 id와 BM25점수가 들어있음
            TopDocs top = searcher.search(q, Math.max(k, 1));   // 검색자로 검색용 인덱스를 조립한 쿼리로 검색해서 점수가 높은 순으로 k 갯수 만큼 빼냄(생성한 내부 문서의 id와 BM25 점수)
            StoredFields stored = searcher.storedFields();  //IndexWriter writer로 생성한 저장장소를 읽는 도구(stored된건 id라서 id필드만)
            List<ChatBotScoredDocDTO> out = new ArrayList<>(top.scoreDocs.length);    //TopDocs로 뽑아낸 점수문서 갯수 만큼 out문서 리스트를 만들고
            for (ScoreDoc sd : top.scoreDocs) {
                String id = stored.document(sd.doc).get(FIELD_ID);  //뽑아낸 점수 문서에서 반환받을 id를 뽑아냄
                out.add(ChatBotScoredDocDTO.builder().id(id).score(sd.score).build());   //그걸 ChatBotScoredDocDTO형식으로 만들어서 적재
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("Lucene BM25 검색 실패: " + query, e);
        }

        /**
         * searcher로 k갯수 만큼 Lucene에서 만든 상위 점수 문서를 빼냄
         * stored로 IndexWriter writer로 생성한 저장장소를 읽어옴
         * 읽어온 저장장소에서 뽑아온 문서와 일치하는 id를 뽑아내서 점수와 함께 out에 저장
         */
    }

    /** 텍스트를 보유 Analyzer로 분석해 토큰 집합(중복 제거)으로 반환. */
    private Set<String> analyze(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>(); //질의쪽의 토큰들(중복제거)
        try (TokenStream ts = analyzer.tokenStream(FIELD_TEXT, text)) { //한글 분석기에선 무시하지만 필드명이 꼭 필요해서 인자값으로 넣었음(아무거나 상관없음) 그저 질문 내용을 분석기로 단어 쪼개는 용도
            CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);  //토큰을 단어로 읽어오기 위한 속성
            ts.reset(); //TokenStream의 처음으로 가기(읽기전에 필수)
            while (ts.incrementToken()) {   //다음 토큰이 있으면
                tokens.add(term.toString());    //토큰을 읽어와서 적재함
            }
            ts.end();   //토큰 스트림끝 알림
        } catch (IOException e) {
            throw new UncheckedIOException("Lucene 질의 분석 실패: " + text, e);
        }
        return tokens;
    }

    @Override
    public void close() {
        try {
            reader.close();
            directory.close();
        } catch (IOException ignored) {
            // best-effort
        }
        analyzer.close();
    }
}