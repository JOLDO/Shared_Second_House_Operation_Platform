package com.busanit401.spring_back.domain.service.rag;

import com.busanit401.spring_back.exception.ChatBotGeminiApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * webClient로 임베딩 요청형식에 맞춰 요청을 보내고, 응답형식에 맞춰 데이터를 응답을 받아서
 * GeminiEmbeddingClientForRag와 거의 동일
 * Gemini 생성(generateContent) 호출 클라이언트 — RAG의 G(답변 생성) 단계.
 */
public class GeminiChatClientForRag {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    /** 공용 {@link WebClient}(스프링 빈)를 주입받는 생성자. */
    public GeminiChatClientForRag(WebClient webClient, String apiKey, String model) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    /** 독립 실행·테스트용: WebClient를 자체 생성. */
    public GeminiChatClientForRag(String apiKey, String model) {
        this(WebClient.builder().baseUrl(BASE_URL).build(), apiKey, model);
    }

    /** 프롬프트로 생성 호출 → 응답 텍스트 반환. */
    public String generate(String prompt) {
        try {
            JsonNode response = webClient.post()
                    .uri(BASE_URL + "/{model}:generateContent?key={key}", model, apiKey)    //공용 WebClient(baseUrl 없음)라 절대 URL로 직접 지정
                    .bodyValue(buildRequestBody(prompt))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return extractText(response);
        } catch (WebClientResponseException e) {
            throw new ChatBotGeminiApiException(
                    HttpStatus.BAD_GATEWAY,
                    resolveGeminiErrorCode(e),
                    buildGeminiHttpErrorMessage(e));
        }
    }

    private Map<String, Object> buildRequestBody(String prompt) {
        return Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.7, //창의성
                        "maxOutputTokens", 2048,    //출력길이 상한
                        // 2.5 Flash는 thinking 토큰이 maxOutputTokens에 포함됨 → FAQ 답변엔 불필요하므로 0으로 꺼서 답변 잘림 방지
                        "thinkingConfig", Map.of("thinkingBudget", 0)));
    }

    private String extractText(JsonNode response) {
        if (response == null) {
            throw new ChatBotGeminiApiException(HttpStatus.BAD_GATEWAY, "EMPTY_RESPONSE",
                    "Gemini 생성 응답이 비어 있습니다.");
        }
        // candidates[0].content.parts[0].text
        JsonNode text = response.path("candidates").path(0)
                .path("content").path("parts").path(0).path("text");
        if (!text.isTextual() || text.asText().isBlank()) {
            throw new ChatBotGeminiApiException(HttpStatus.BAD_GATEWAY, "INVALID_RESPONSE",
                    "Gemini 생성 응답에 text가 없습니다.");
        }
        return text.asText();
    }

    private String resolveGeminiErrorCode(WebClientResponseException e) {
        String body = e.getResponseBodyAsString();
        int status = e.getStatusCode().value();
        if (body != null && body.contains("no longer available to new users")) {
            return "MODEL_UNAVAILABLE";
        }
        if (status == 400 || status == 401 || status == 403) {
            return "INVALID_API_KEY_OR_REQUEST";
        }
        if (status == 429) {
            return "RATE_LIMITED";
        }
        if (e.getStatusCode().is5xxServerError()) {
            return "UPSTREAM_TEMPORARY_ERROR";
        }
        return "GEMINI_HTTP_ERROR";
    }

    private String buildGeminiHttpErrorMessage(WebClientResponseException e) {
        String body = e.getResponseBodyAsString();
        int status = e.getStatusCode().value();
        if (body != null && body.contains("no longer available to new users")) {
            return "현재 설정된 Gemini 모델을 사용할 수 없습니다. 최신 지원 모델명으로 변경해주세요.";
        }
        if (status == 400 || status == 401 || status == 403) {
            return "Gemini API 키가 올바르지 않거나 요청이 잘못되었습니다.";
        }
        if (status == 429) {
            return "AI 요청이 잠시 많습니다. 잠시 후 다시 시도해주세요.";
        }
        if (e.getStatusCode().is5xxServerError()) {
            return "Gemini 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.";
        }
        return "Gemini 답변 생성에 실패했습니다. upstream status=" + status;
    }
}