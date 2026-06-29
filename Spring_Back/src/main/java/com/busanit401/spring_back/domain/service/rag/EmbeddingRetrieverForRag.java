package com.busanit401.spring_back.domain.service.rag;

import com.busanit401.spring_back.dto.ChatBotFaqDocDTO;
import com.busanit401.spring_back.dto.ChatBotScoredDocDTO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 생성자 호출 시 문서의 dim이 일정한지 확인하고 코사인 유사도의 분모로 활용할 문서의 벡터들의 빗변길이를 구한다.
 * QueryEmbeddingProviderForRag에서 만들어서 반환하는 임베딩벡터 배열을 받아온다.
 * 벡터는 0x 5DD5403D 7D7837BB 4244493B 8BC496BB 이렇게 4바이트씩 끊어서 1차원씩 읽히고 파싱되어 온 값을 이용
 * 질의요청문과 db에 저장된 한 문서의 각 차원 곱의 합이 내적이 된다.
 * searchScored는 질의 요청문의 벡터들의 빗변길이를 구한 후 코사인 유사도를 계산하여 점수를 계산한다
 * 코사인 유사도 = 내적 / (질의 요청문의 벡터들의 빗변길이 × 문서의 벡터들의 빗변길이)
 * 점수를 구한 뒤 0보다 큰 값들 중 정렬하여 상위 k의 (id+점수)리스트를 반환한다.
 */
public class EmbeddingRetrieverForRag implements ScoringRetrieverForRag {

    private final String name;
    private final QueryEmbeddingProviderForRag provider;
    private final String[] ids;
    private final float[][] vectors;
    private final double[] norms;
    private final int dim;

    public EmbeddingRetrieverForRag(String name, List<ChatBotFaqDocDTO> corpus, QueryEmbeddingProviderForRag provider) {
        this.name = name;
        this.provider = provider;
        int n = corpus.size();
        this.ids = new String[n];
        this.vectors = new float[n][];
        this.norms = new double[n];
        int d = -1;
        for (int i = 0; i < n; i++) {   //문서들의 norm값을 계산 해놓음(벡터의 길이[대각선])
            ChatBotFaqDocDTO doc = corpus.get(i);
            float[] emb = doc.getEmbedding();
            if (emb == null || emb.length == 0) {
                throw new IllegalArgumentException("문서 임베딩이 비어 있음: id=" + doc.getId());
            }
            if (d < 0) {
                d = emb.length;            // 첫 문서 차원을 기준으로 삼고
            } else if (emb.length != d) {  // 이후 문서는 모두 같은 차원이어야 내적/코사인이 성립
                throw new IllegalArgumentException(
                        "문서 임베딩 차원 불일치: id=" + doc.getId() + " (" + emb.length + " ≠ " + d + ")");
            }
            ids[i] = doc.getId();
            vectors[i] = emb;
            norms[i] = norm(emb);
        }
        this.dim = d;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<ChatBotScoredDocDTO> searchScored(String query, int k) {
        float[] q = provider.embed(query);
        if (q == null || q.length == 0) {
            return List.of();   // 캐시에 질의 임베딩 없음
        }
        if (dim >= 0 && q.length != dim) {
            throw new IllegalArgumentException(
                    "질의 임베딩 차원(" + q.length + ")이 문서 차원(" + dim + ")과 다름");
        }
        double qNorm = norm(q);
        if (qNorm == 0.0) {
            return List.of();
        }

        // 질의 벡터와 모든 문서 벡터의 코사인 유사도 = 내적 / (질의노름 × 문서노름).
        // 단어가 안 겹쳐도 의미가 가까우면 벡터 방향이 비슷해 코사인이 높게 나온다(임베딩의 강점).
        List<ChatBotScoredDocDTO> scored = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            if (norms[i] == 0.0) {
                continue;            // 영벡터 문서(0 나눗셈 방지)
            }
            double dot = 0.0;
            float[] v = vectors[i]; //각 문서의 벡터
            for (int j = 0; j < v.length; j++) {   // 차원별 곱의 합 = 내적
                dot += q[j] * v[j]; //차원별 곱의 합을 누적
            }
            double cosine = dot / (qNorm * norms[i]); //차원별 곱(질의, 문서)의 합 누적을 벡터길이로 나눈 코사인 유사도로 점수를 매김
            if (cosine > 0.0) { //1 -> 유사함, 0 -> 무관, -1 -> 반대
                scored.add(ChatBotScoredDocDTO.builder().id(ids[i]).score(cosine).build());
            }
        }
        scored.sort(Comparator.comparingDouble(ChatBotScoredDocDTO::getScore).reversed()
                .thenComparing(ChatBotScoredDocDTO::getId));
        return scored.subList(0, Math.min(k, scored.size()));   // 상위 k개
    }

    /** 벡터의 L2 노름(길이) = √(Σ x²). 코사인 분모로 사용. */
    private static double norm(float[] v) {
        double s = 0.0;
        for (float x : v) {
            s += (double) x * x;
        }
        return Math.sqrt(s);
    }
}
