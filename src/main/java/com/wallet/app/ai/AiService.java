package com.wallet.app.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiService {

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);

    private final AiContextBuilder contextBuilder;
    private final AiPromptBuilder promptBuilder;
    private final AiClient aiClient;
    private final AiActionMapper actionMapper;
    private final int perMinuteLimit;

    private final Map<UUID, Deque<Long>> requestWindows = new ConcurrentHashMap<>();

    public AiService(
        AiContextBuilder contextBuilder,
        AiPromptBuilder promptBuilder,
        AiClient aiClient,
        AiActionMapper actionMapper,
        @Value("${ai.rate-limit.per-minute:30}") int perMinuteLimit
    ) {
        this.contextBuilder = contextBuilder;
        this.promptBuilder = promptBuilder;
        this.aiClient = aiClient;
        this.actionMapper = actionMapper;
        this.perMinuteLimit = perMinuteLimit;
    }

    public AiChatResponse handle(String username, AiChatRequest request) {
        AiContext context = contextBuilder.build(username, request.currentPage());
        enforceRateLimit(context.userId());

        String responseId = UUID.randomUUID().toString();
        AiChatResponse fallback = fallbackResponse(context, request.query(), responseId);

        try {
            String systemPrompt = promptBuilder.buildSystemPrompt();
            String userPrompt = promptBuilder.buildUserPrompt(context, request);
            String raw = aiClient.generate(systemPrompt, userPrompt);
            AiChatResponse mapped = actionMapper.fromRawOrFallback(raw, responseId, fallback);
            audit(context, request.query(), mapped, true);
            return mapped;
        } catch (Exception exception) {
            logger.warn("AI provider unavailable. Falling back to deterministic assistant. reason={}", exception.getMessage());
            audit(context, request.query(), fallback, false);
            return fallback;
        }
    }

    private void enforceRateLimit(UUID userId) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000;

        Deque<Long> calls = requestWindows.computeIfAbsent(userId, key -> new ArrayDeque<>());

        synchronized (calls) {
            while (!calls.isEmpty() && calls.peekFirst() < windowStart) {
                calls.pollFirst();
            }

            if (calls.size() >= perMinuteLimit) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI request limit reached. Please retry in a minute.");
            }

            calls.addLast(now);
        }
    }

    private AiChatResponse fallbackResponse(AiContext context, String query, String responseId) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        if (normalizedQuery.contains("balance")) {
            String message = "Your current wallet balance is " + formatAmount(context.balance()) + " " + context.currency() + ".";
            return new AiChatResponse(responseId, message, null, defaultSuggestions(context.currentPage()));
        }

        if (normalizedQuery.contains("recent transaction") || normalizedQuery.contains("last transaction") || normalizedQuery.contains("transaction")) {
            return new AiChatResponse(
                responseId,
                summarizeTransactions(context),
                new AiAction("NAVIGATE", "/transactions", Map.of()),
                List.of("Show only outgoing", "Show only incoming", "Filter by status")
            );
        }

        if ((normalizedQuery.startsWith("did i") || normalizedQuery.contains("have i")) && normalizedQuery.contains("send")) {
            String nameHint = extractPotentialName(query);
            String message;
            if (nameHint == null) {
                message = "I can confirm transfer history from available transaction records, but I need a recipient name or reference. I can open Transactions for you.";
            } else {
                boolean found = hasTransactionReferenceHint(context, nameHint);
                message = found
                    ? "I found at least one recent transaction with reference/note matching '" + nameHint + "'. Please verify details in Transactions."
                    : "I could not confidently match '" + nameHint + "' in recent references/notes. Open Transactions to verify by date and amount.";
            }

            return new AiChatResponse(
                responseId,
                message,
                new AiAction("NAVIGATE", "/transactions", Map.of()),
                List.of("Show recent outgoing", "Filter by amount", "Open contacts")
            );
        }

        if (normalizedQuery.contains("add money") || normalizedQuery.contains("top up")) {
            return new AiChatResponse(
                responseId,
                "To add funds securely, open Add Money and complete the Razorpay flow. I can take you there now.",
                new AiAction("NAVIGATE", "/add-money", Map.of()),
                List.of("Add INR 500", "Add INR 1000", "Check my wallet balance")
            );
        }

        if (normalizedQuery.contains("withdraw")) {
            if (normalizedQuery.contains("bank") || normalizedQuery.contains("add account") || normalizedQuery.contains("add-bank")) {
                return new AiChatResponse(
                    responseId,
                    "Opening Add Bank Account in the withdrawal center. Please verify details before saving.",
                    new AiAction("OPEN_MODAL", "/withdraw", Map.of("modal", "add-bank")),
                    List.of("What is IFSC format?", "Prefill withdrawal amount", "Show withdrawal history")
                );
            }

            String amount = extractAmount(normalizedQuery);
            if (amount != null) {
                return new AiChatResponse(
                    responseId,
                    "I can prefill a withdrawal request for INR " + amount + ". Please review and submit it yourself.",
                    new AiAction("PREFILL_FORM", "/withdraw", Map.of("amount", amount)),
                    List.of("Open bank account modal", "Show withdrawal history", "Check available balance")
                );
            }

            return new AiChatResponse(
                responseId,
                "Withdrawal is simulated securely: choose a linked bank account, enter amount, and submit. Status moves from PENDING to SUCCESS/FAILED.",
                new AiAction("NAVIGATE", "/withdraw", Map.of()),
                List.of("Add bank account", "Show withdrawal history", "How long does withdrawal take?")
            );
        }

        if (normalizedQuery.contains("send") && normalizedQuery.contains("money")) {
            String amount = extractAmount(normalizedQuery);
            String receiver = extractPotentialName(query);
            Map<String, String> payload = new ConcurrentHashMap<>();
            if (amount != null) {
                payload.put("amount", amount);
            }
            if (receiver != null) {
                payload.put("receiverName", receiver);
            }

            String message = amount == null
                ? "I can guide you to Send Money. You can choose contact/email and review before submitting."
                : "I can prefill Send Money with INR " + amount + " so you can review and submit securely.";

            return new AiChatResponse(
                responseId,
                message,
                new AiAction("PREFILL_FORM", "/send-money", payload),
                List.of("Use recipient email", "Scan recipient QR", "Open contacts")
            );
        }

        if (normalizedQuery.contains("go to") || normalizedQuery.startsWith("open ")) {
            String path = mapNavigationPath(normalizedQuery);
            if (path != null) {
                return new AiChatResponse(
                    responseId,
                    "Navigating to the requested section.",
                    new AiAction("NAVIGATE", path, Map.of()),
                    defaultSuggestions(path)
                );
            }
        }

        if (normalizedQuery.contains("kyc")) {
            return new AiChatResponse(
                responseId,
                "KYC verifies identity for compliance and risk control. In this app, upload documents in Profile, then status updates as PENDING, VERIFIED, or REJECTED.",
                new AiAction("NAVIGATE", "/profile", Map.of("highlight", "kyc")),
                List.of("Open profile", "What documents are accepted?", "How long does verification take?")
            );
        }

        if (normalizedQuery.contains("spending") || normalizedQuery.contains("weekly") || normalizedQuery.contains("insight")) {
            return new AiChatResponse(
                responseId,
                summarizeWeeklyInsights(context),
                new AiAction("NAVIGATE", "/analytics", Map.of()),
                List.of("Open analytics", "Show outgoing transactions", "Any unusual activity?")
            );
        }

        return new AiChatResponse(
            responseId,
            genericHelpMessage(context),
            null,
            defaultSuggestions(context.currentPage())
        );
    }

    private String summarizeTransactions(AiContext context) {
        List<AiContext.AiTransactionContext> recent = context.recentTransactions();
        if (recent.isEmpty()) {
            return "No recent transactions found. You can start by adding money or sending money from your dashboard.";
        }

        List<String> lines = new ArrayList<>();
        recent.stream().limit(5).forEach((item) -> {
            String prefix = "IN".equals(item.direction()) ? "+" : "-";
            lines.add(prefix + formatAmount(item.amount()) + " " + item.currency() + " (" + item.type() + ", " + item.status() + ")");
        });

        return "Your recent transactions: " + String.join(" | ", lines);
    }

    private String summarizeWeeklyInsights(AiContext context) {
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);

        BigDecimal outgoing = context.recentTransactions().stream()
            .filter((item) -> "OUT".equals(item.direction()))
            .filter((item) -> item.createdAt() != null && OffsetDateTime.parse(item.createdAt()).isAfter(sevenDaysAgo))
            .map(AiContext.AiTransactionContext::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal incoming = context.recentTransactions().stream()
            .filter((item) -> "IN".equals(item.direction()))
            .filter((item) -> item.createdAt() != null && OffsetDateTime.parse(item.createdAt()).isAfter(sevenDaysAgo))
            .map(AiContext.AiTransactionContext::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        String trend = outgoing.compareTo(incoming) > 0
            ? "Your outflow is higher than inflow this week."
            : "Your inflow is healthy compared to outflow this week.";

        return "7-day summary: outgoing " + formatAmount(outgoing) + " " + context.currency()
            + ", incoming " + formatAmount(incoming) + " " + context.currency() + ". " + trend
            + " Unread alerts: " + context.notifications().unreadCount() + ".";
    }

    private String genericHelpMessage(AiContext context) {
        return "I can help with balances, recent transactions, spending insights, and guided flows like Send Money, Add Money, and Withdraw. You are currently on "
            + context.currentPage() + ".";
    }

    private List<String> defaultSuggestions(String currentPage) {
        if (currentPage == null) {
            return List.of("Check balance", "Show recent transactions", "Open send money");
        }

        return switch (currentPage) {
            case "/dashboard", "dashboard" -> List.of("Check spending this week", "Show recent transactions", "Open send money");
            case "/wallet", "wallet" -> List.of("Check balance", "Open add money", "Show wallet activity");
            case "/withdraw", "withdraw" -> List.of("How does withdrawal work?", "Open bank account modal", "Prefill withdrawal amount");
            case "/transactions", "transactions" -> List.of("Show outgoing only", "Find failed transactions", "Open analytics");
            default -> List.of("Check balance", "Show recent transactions", "Open send money");
        };
    }

    private String extractAmount(String query) {
        Matcher matcher = AMOUNT_PATTERN.matcher(query);
        if (!matcher.find()) {
            return null;
        }

        try {
            BigDecimal amount = new BigDecimal(matcher.group(1)).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return amount.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String extractPotentialName(String rawQuery) {
        String lowered = rawQuery.toLowerCase(Locale.ROOT);
        int toIndex = lowered.indexOf(" to ");
        if (toIndex < 0) {
            return null;
        }

        String suffix = rawQuery.substring(toIndex + 4).trim();
        if (suffix.isBlank()) {
            return null;
        }

        String[] parts = suffix.split("\\s+");
        if (parts.length == 0) {
            return null;
        }

        String name = parts[0].replaceAll("[^A-Za-z]", "").trim();
        if (name.length() < 2) {
            return null;
        }
        return name;
    }

    private String mapNavigationPath(String query) {
        List<Map.Entry<String, String>> mappings = List.of(
            Map.entry("dashboard", "/dashboard"),
            Map.entry("wallet", "/wallet"),
            Map.entry("transaction", "/transactions"),
            Map.entry("withdraw", "/withdraw"),
            Map.entry("add money", "/add-money"),
            Map.entry("send", "/send-money"),
            Map.entry("contact", "/contacts"),
            Map.entry("analytics", "/analytics"),
            Map.entry("notification", "/notifications"),
            Map.entry("profile", "/profile"),
            Map.entry("security", "/security"),
            Map.entry("settings", "/settings")
        );

        return mappings.stream()
            .filter((entry) -> query.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .min(Comparator.comparingInt(String::length))
            .orElse(null);
    }

    private boolean hasTransactionReferenceHint(AiContext context, String hint) {
        String normalizedHint = hint.toLowerCase(Locale.ROOT);
        return context.recentTransactions().stream().anyMatch((item) -> {
            String reference = item.reference() == null ? "" : item.reference().toLowerCase(Locale.ROOT);
            return reference.contains(normalizedHint);
        });
    }

    private String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private void audit(AiContext context, String query, AiChatResponse response, boolean providerUsed) {
        String actionType = response.action() == null ? "NONE" : response.action().type();
        logger.info(
            "AI_AUDIT userId={} page={} providerUsed={} action={} queryChars={} responseId={}",
            context.userId(),
            context.currentPage(),
            providerUsed,
            actionType,
            query == null ? 0 : query.length(),
            response.responseId()
        );
    }
}
