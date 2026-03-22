package com.wallet.app.ai;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.wallet.app.entity.Notification;
import com.wallet.app.entity.Transaction;
import com.wallet.app.entity.User;
import com.wallet.app.entity.Wallet;
import com.wallet.app.repository.NotificationRepository;
import com.wallet.app.repository.TransactionRepository;
import com.wallet.app.repository.UserRepository;
import com.wallet.app.repository.WalletRepository;

@Component
public class AiContextBuilder {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationRepository notificationRepository;

    public AiContextBuilder(
        UserRepository userRepository,
        WalletRepository walletRepository,
        TransactionRepository transactionRepository,
        NotificationRepository notificationRepository
    ) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.notificationRepository = notificationRepository;
    }

    public AiContext build(String username, String currentPage) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));

        Wallet wallet = walletRepository.findByUserId(user.getId()).orElse(null);

        List<AiContext.AiTransactionContext> transactions = wallet == null
            ? Collections.emptyList()
            : buildTransactionsContext(wallet);

        AiContext.AiNotificationSummary notificationSummary = buildNotificationSummary(user.getId());

        return new AiContext(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            normalizePage(currentPage),
            wallet == null ? BigDecimal.ZERO : wallet.getBalance(),
            wallet == null ? "INR" : wallet.getCurrency(),
            wallet == null ? "NOT_CREATED" : wallet.getStatus(),
            transactions,
            notificationSummary
        );
    }

    private List<AiContext.AiTransactionContext> buildTransactionsContext(Wallet wallet) {
        UUID walletId = wallet.getId();

        return transactionRepository.findTop100ByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(walletId, walletId)
            .stream()
            .limit(10)
            .map((item) -> mapTransaction(item, walletId))
            .toList();
    }

    private AiContext.AiTransactionContext mapTransaction(Transaction item, UUID walletId) {
        boolean incoming = walletId.equals(item.getToWalletId());
        UUID counterpartyWalletId = incoming ? item.getFromWalletId() : item.getToWalletId();

        return new AiContext.AiTransactionContext(
            incoming ? "IN" : "OUT",
            item.getAmount(),
            item.getCurrency(),
            upper(item.getTransactionType()),
            upper(item.getStatus()),
            sanitize(item.getReference(), 80),
            maskId(counterpartyWalletId),
            item.getCreatedAt() == null ? null : item.getCreatedAt().toString()
        );
    }

    private AiContext.AiNotificationSummary buildNotificationSummary(UUID userId) {
        List<Notification> recent = notificationRepository.findTop200ByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .limit(10)
            .toList();

        long unreadCount = notificationRepository.countByUserIdAndReadFalse(userId);
        List<String> titles = recent.stream().map((item) -> sanitize(item.getTitle(), 80)).toList();
        List<String> types = recent.stream().map((item) -> item.getType().name()).toList();

        return new AiContext.AiNotificationSummary(unreadCount, titles, types);
    }

    private String normalizePage(String page) {
        if (page == null || page.isBlank()) {
            return "unknown";
        }
        return page.trim().toLowerCase(Locale.ROOT);
    }

    private String upper(String value) {
        if (value == null) {
            return "UNKNOWN";
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private String sanitize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String maskId(UUID value) {
        if (value == null) {
            return "EXTERNAL";
        }
        String text = value.toString();
        return text.substring(0, 6) + "..." + text.substring(text.length() - 4);
    }
}
