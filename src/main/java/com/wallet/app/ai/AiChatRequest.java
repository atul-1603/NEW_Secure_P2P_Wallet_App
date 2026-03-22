package com.wallet.app.ai;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiChatRequest(
    @NotBlank(message = "query is required")
    @Size(max = 500, message = "query must be at most 500 characters")
    String query,

    @Size(max = 120, message = "currentPage must be at most 120 characters")
    String currentPage,

    @Valid
    @Size(max = 20, message = "conversation can include at most 20 messages")
    List<AiConversationItem> conversation
) {
}
