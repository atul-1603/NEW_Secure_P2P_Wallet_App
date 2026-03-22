package com.wallet.app.ai;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class AiPromptBuilder {

    public String buildSystemPrompt() {
        return """
            You are Secure Wallet AI, a regulated fintech assistant for a P2P wallet app.

            Strict rules:
            1) Never invent balances, transactions, limits, contacts, or any financial fact.
            2) Use ONLY the context data provided in the user prompt.
            3) Never claim to execute money movement. You can only guide users or suggest UI actions.
            4) Keep responses concise, clear, and supportive.
            5) If data is missing, explicitly say what is unavailable.
            6) Never request OTP, passwords, raw bank details, or full card/account numbers.
            7) If user asks for harmful/fraud behavior, refuse and suggest safe alternatives.

            Output MUST be valid JSON only with this schema:
            {
              "message": "string",
              "action": {
                "type": "NAVIGATE|OPEN_MODAL|PREFILL_FORM|HIGHLIGHT_SECTION",
                "target": "string",
                "payload": { "key": "value" }
              } | null,
              "suggestions": ["string", "string", "string"]
            }

            Keep suggestions short and app-relevant.
            """;
    }

    public String buildUserPrompt(AiContext context, AiChatRequest request) {
        List<Map<String, String>> history = request.conversation() == null
            ? List.of()
            : request.conversation().stream()
                .limit(8)
                .map((item) -> Map.of(
                    "role", safe(item.role()),
                    "message", safe(item.message())
                ))
                .toList();

        Map<String, Object> payload = Map.of(
            "currentPage", safe(context.currentPage()),
            "user", Map.of(
                "username", safe(context.username()),
                "fullName", safe(context.fullName())
            ),
            "wallet", Map.of(
                "balance", context.balance(),
                "currency", safe(context.currency()),
                "status", safe(context.walletStatus())
            ),
            "recentTransactions", context.recentTransactions(),
            "notifications", context.notifications(),
            "conversation", history,
            "userQuery", safe(request.query())
        );

        return "Context JSON:\n" + payload + "\n\nUser Query:\n" + safe(request.query());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
