package com.wallet.app.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AiConversationItem(
    @NotBlank(message = "role is required")
    @Pattern(regexp = "^(user|assistant|system)$", flags = Pattern.Flag.CASE_INSENSITIVE, message = "role must be user, assistant, or system")
    String role,

    @NotBlank(message = "message is required")
    @Size(max = 1000, message = "message must be at most 1000 characters")
    String message
) {
}
