package com.wallet.app.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AiClient {

    private final String provider;
    private final String openAiApiKey;
    private final String openAiModel;
    private final String openAiUrl;
    private final String geminiApiKey;
    private final String geminiModel;
    private final String geminiUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiClient(
        @Value("${ai.provider:NONE}") String provider,
        @Value("${ai.openai.api-key:}") String openAiApiKey,
        @Value("${ai.openai.model:gpt-4o-mini}") String openAiModel,
        @Value("${ai.openai.url:https://api.openai.com/v1/chat/completions}") String openAiUrl,
        @Value("${ai.gemini.api-key:}") String geminiApiKey,
        @Value("${ai.gemini.model:gemini-1.5-flash}") String geminiModel,
        @Value("${ai.gemini.url:https://generativelanguage.googleapis.com/v1beta/models}") String geminiUrl,
        ObjectMapper objectMapper
    ) {
        this.provider = provider == null ? "NONE" : provider.trim().toUpperCase();
        this.openAiApiKey = openAiApiKey;
        this.openAiModel = openAiModel;
        this.openAiUrl = openAiUrl;
        this.geminiApiKey = geminiApiKey;
        this.geminiModel = geminiModel;
        this.geminiUrl = geminiUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String generate(String systemPrompt, String userPrompt) {
        return switch (provider) {
            case "OPENAI" -> callOpenAi(systemPrompt, userPrompt);
            case "GEMINI" -> callGemini(systemPrompt, userPrompt);
            default -> throw new IllegalStateException("AI provider is disabled");
        };
    }

    private String callOpenAi(String systemPrompt, String userPrompt) {
        requireKey(openAiApiKey, "OPENAI");

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "model", openAiModel,
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
                )
            ));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(openAiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openAiApiKey)
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new IllegalStateException("OpenAI request failed with status " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                throw new IllegalStateException("OpenAI response content missing");
            }

            return contentNode.asText();
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI request failed", ex);
        }
    }

    private String callGemini(String systemPrompt, String userPrompt) {
        requireKey(geminiApiKey, "GEMINI");

        String endpoint = geminiUrl + "/" + geminiModel + ":generateContent?key=" + geminiApiKey;

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "generationConfig", Map.of(
                    "temperature", 0.2,
                    "responseMimeType", "application/json"
                ),
                "systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", systemPrompt))
                ),
                "contents", List.of(
                    Map.of("parts", List.of(Map.of("text", userPrompt)))
                )
            ));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Gemini request failed with status " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (textNode.isMissingNode() || textNode.isNull()) {
                throw new IllegalStateException("Gemini response content missing");
            }

            return textNode.asText();
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini request failed", ex);
        }
    }

    private void requireKey(String key, String providerName) {
        if (Objects.requireNonNullElse(key, "").isBlank()) {
            throw new IllegalStateException(providerName + " API key is not configured");
        }
    }
}
