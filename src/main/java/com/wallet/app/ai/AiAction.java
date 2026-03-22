package com.wallet.app.ai;

import java.util.Map;

public record AiAction(
    String type,
    String target,
    Map<String, String> payload
) {
}
