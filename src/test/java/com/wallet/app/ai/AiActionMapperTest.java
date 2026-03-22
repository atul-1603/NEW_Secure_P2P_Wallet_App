package com.wallet.app.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class AiActionMapperTest {

    private final AiActionMapper mapper = new AiActionMapper(new ObjectMapper());

    @Test
    void shouldParseStructuredJsonResponse() {
        AiChatResponse fallback = new AiChatResponse("fallback-id", "fallback", null, List.of());

        String raw = """
            {
              "message": "Opening transactions for you.",
              "action": {
                "type": "NAVIGATE",
                "target": "/transactions",
                "payload": {
                  "highlight": "recent"
                }
              },
              "suggestions": ["Show outgoing", "Show failed"]
            }
            """;

        AiChatResponse response = mapper.fromRawOrFallback(raw, "res-1", fallback);

        assertThat(response.message()).isEqualTo("Opening transactions for you.");
        assertThat(response.action()).isNotNull();
        assertThat(response.action().type()).isEqualTo("NAVIGATE");
        assertThat(response.action().target()).isEqualTo("/transactions");
        assertThat(response.action().payload()).isEqualTo(Map.of("highlight", "recent"));
        assertThat(response.suggestions()).containsExactly("Show outgoing", "Show failed");
    }

    @Test
    void shouldReturnFallbackForInvalidJson() {
        AiChatResponse fallback = new AiChatResponse("fallback-id", "fallback", null, List.of("one"));

        AiChatResponse response = mapper.fromRawOrFallback("not-json", "res-2", fallback);

        assertThat(response).isEqualTo(fallback);
    }
}
