package com.wallet.app.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AiActionMapper {

    private static final Set<String> ALLOWED_ACTIONS = Set.of(
        "NAVIGATE",
        "OPEN_MODAL",
        "PREFILL_FORM",
        "HIGHLIGHT_SECTION"
    );

    private final ObjectMapper objectMapper;

    public AiActionMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiChatResponse fromRawOrFallback(String raw, String responseId, AiChatResponse fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            String sanitized = stripCodeFence(raw);
            Map<String, Object> payload = objectMapper.readValue(sanitized, new TypeReference<>() {
            });

            String message = toStringOrNull(payload.get("message"));
            if (message == null || message.isBlank()) {
                return fallback;
            }

            AiAction action = toAction(payload.get("action"));
            List<String> suggestions = toSuggestions(payload.get("suggestions"));

            return new AiChatResponse(responseId, message.trim(), action, suggestions);
        } catch (Exception exception) {
            return fallback;
        }
    }

    private AiAction toAction(Object source) {
        if (!(source instanceof Map<?, ?> sourceMap)) {
            return null;
        }

        String type = toStringOrNull(sourceMap.get("type"));
        if (type == null) {
            return null;
        }

        String normalizedType = type.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ACTIONS.contains(normalizedType)) {
            return null;
        }

        String target = toStringOrNull(sourceMap.get("target"));
        Map<String, String> mappedPayload = Map.of();

        Object payloadValue = sourceMap.get("payload");
        if (payloadValue instanceof Map<?, ?> payloadMap) {
            mappedPayload = payloadMap.entrySet().stream()
                .filter((entry) -> entry.getKey() != null && entry.getValue() != null)
                .collect(
                    java.util.stream.Collectors.toMap(
                        (entry) -> entry.getKey().toString(),
                        (entry) -> entry.getValue().toString(),
                        (left, right) -> right
                    )
                );
        }

        return new AiAction(normalizedType, target, mappedPayload);
    }

    private List<String> toSuggestions(Object source) {
        if (!(source instanceof List<?> items)) {
            return List.of();
        }

        List<String> suggestions = new ArrayList<>();
        for (Object item : items) {
            if (item == null) {
                continue;
            }
            String text = item.toString().trim();
            if (!text.isBlank()) {
                suggestions.add(text);
            }
            if (suggestions.size() == 3) {
                break;
            }
        }

        return suggestions;
    }

    private String stripCodeFence(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            String[] lines = trimmed.split("\\n");
            if (lines.length >= 3) {
                StringBuilder builder = new StringBuilder();
                for (int i = 1; i < lines.length - 1; i++) {
                    builder.append(lines[i]).append('\n');
                }
                return builder.toString().trim();
            }
        }
        return trimmed;
    }

    private String toStringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isBlank() ? null : text;
    }
}
