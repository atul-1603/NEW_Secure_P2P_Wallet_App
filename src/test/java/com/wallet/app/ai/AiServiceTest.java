package com.wallet.app.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AiServiceTest {

    @Test
    void shouldAnswerBalanceUsingFallbackWhenProviderIsUnavailable() {
        AiContextBuilder contextBuilder = mock(AiContextBuilder.class);
        AiPromptBuilder promptBuilder = mock(AiPromptBuilder.class);
        AiClient aiClient = mock(AiClient.class);
        AiActionMapper actionMapper = mock(AiActionMapper.class);

        AiService service = new AiService(contextBuilder, promptBuilder, aiClient, actionMapper, 30);

        AiContext context = new AiContext(
            UUID.randomUUID(),
            "demo",
            "Demo User",
            "/dashboard",
            new BigDecimal("1234.50"),
            "INR",
            "ACTIVE",
            List.of(),
            new AiContext.AiNotificationSummary(2, List.of("Money sent"), List.of("DEBIT"))
        );

        when(contextBuilder.build(anyString(), anyString())).thenReturn(context);
        when(promptBuilder.buildSystemPrompt()).thenReturn("system");
        when(promptBuilder.buildUserPrompt(context, new AiChatRequest("What is my balance?", "/dashboard", List.of())))
            .thenReturn("user");
        when(aiClient.generate(anyString(), anyString())).thenThrow(new IllegalStateException("disabled"));

        AiChatResponse response = service.handle("demo", new AiChatRequest("What is my balance?", "/dashboard", List.of()));

        assertThat(response.message()).contains("1234.50").contains("INR");
        assertThat(response.action()).isNull();
        assertThat(response.suggestions()).isNotEmpty();
    }
}
