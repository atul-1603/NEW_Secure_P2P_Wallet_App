package com.wallet.app.ai;

import java.util.List;

public record AiChatResponse(
    String responseId,
    String message,
    AiAction action,
    List<String> suggestions
) {
}
