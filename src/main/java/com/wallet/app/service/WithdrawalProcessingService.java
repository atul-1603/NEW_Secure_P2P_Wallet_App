package com.wallet.app.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.wallet.app.entity.Wallet;
import com.wallet.app.entity.WithdrawalRequest;
import com.wallet.app.entity.NotificationType;
import com.wallet.app.repository.TransactionRepository;
import com.wallet.app.repository.WalletRepository;
import com.wallet.app.repository.WithdrawalRequestRepository;

@Service
public class WithdrawalProcessingService {

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionTemplate transactionTemplate;
    private final NotificationService notificationService;

    public WithdrawalProcessingService(
        WithdrawalRequestRepository withdrawalRequestRepository,
        WalletRepository walletRepository,
        TransactionRepository transactionRepository,
        TransactionTemplate transactionTemplate,
        NotificationService notificationService
    ) {
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.transactionTemplate = transactionTemplate;
        this.notificationService = notificationService;
    }

    @Async("withdrawalProcessingExecutor")
    public void processAsync(UUID withdrawalId) {
        try {
            transactionTemplate.executeWithoutResult(status -> markProcessing(withdrawalId));

            Thread.sleep(ThreadLocalRandom.current().nextLong(5000L, 10001L));

            boolean success = ThreadLocalRandom.current().nextInt(100) < 92;
            if (success) {
                transactionTemplate.executeWithoutResult(status -> markSuccess(withdrawalId));
            } else {
                transactionTemplate.executeWithoutResult(status -> markFailedAndRefund(withdrawalId));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            transactionTemplate.executeWithoutResult(status -> markFailedAndRefund(withdrawalId));
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> markFailedAndRefund(withdrawalId));
        }
    }

    void markProcessing(UUID withdrawalId) {
        UUID nonNullWithdrawalId = Objects.requireNonNull(withdrawalId, "withdrawalId is required");
        Optional<WithdrawalRequest> maybeRequest = withdrawalRequestRepository.findById(nonNullWithdrawalId);
        if (maybeRequest.isEmpty()) {
            return;
        }

        WithdrawalRequest request = maybeRequest.get();
        if (!"PENDING".equalsIgnoreCase(request.getStatus())) {
            return;
        }

        request.setStatus("PROCESSING");
        withdrawalRequestRepository.save(request);
        updateTransactionStatus(request.getReferenceId(), "PENDING", null);
    }

    void markSuccess(UUID withdrawalId) {
        UUID nonNullWithdrawalId = Objects.requireNonNull(withdrawalId, "withdrawalId is required");
        Optional<WithdrawalRequest> maybeRequest = withdrawalRequestRepository.findById(nonNullWithdrawalId);
        if (maybeRequest.isEmpty()) {
            return;
        }

        WithdrawalRequest request = maybeRequest.get();
        if (!"PROCESSING".equalsIgnoreCase(request.getStatus())) {
            return;
        }

        request.setStatus("SUCCESS");
        request.setProcessedAt(OffsetDateTime.now());
        withdrawalRequestRepository.save(request);
        updateTransactionStatus(request.getReferenceId(), "SUCCESS", request.getProcessedAt());

        notificationService.createAndPublish(
            request.getUserId(),
            NotificationType.WITHDRAWAL,
            "Withdrawal completed",
            "INR " + request.getAmount() + " withdrawal has been processed successfully"
        );
    }

    void markFailedAndRefund(UUID withdrawalId) {
        UUID nonNullWithdrawalId = Objects.requireNonNull(withdrawalId, "withdrawalId is required");
        Optional<WithdrawalRequest> maybeRequest = withdrawalRequestRepository.findById(nonNullWithdrawalId);
        if (maybeRequest.isEmpty()) {
            return;
        }

        WithdrawalRequest request = maybeRequest.get();
        if (!"PROCESSING".equalsIgnoreCase(request.getStatus()) && !"PENDING".equalsIgnoreCase(request.getStatus())) {
            return;
        }

        Wallet wallet = walletRepository.findByUserId(request.getUserId())
            .flatMap(current -> walletRepository.findByIdForUpdate(current.getId()))
            .orElse(null);

        if (wallet != null) {
            wallet.setBalance(wallet.getBalance().add(request.getAmount()));
            walletRepository.save(wallet);
        }

        request.setStatus("FAILED");
        request.setProcessedAt(OffsetDateTime.now());
        withdrawalRequestRepository.save(request);
        updateTransactionStatus(request.getReferenceId(), "FAILED", request.getProcessedAt());

        notificationService.createAndPublish(
            request.getUserId(),
            NotificationType.WITHDRAWAL,
            "Withdrawal failed",
            "INR " + request.getAmount() + " withdrawal failed and amount has been refunded"
        );
    }

    private void updateTransactionStatus(UUID referenceId, String status, OffsetDateTime completedAt) {
        transactionRepository.findByReference(referenceId.toString()).ifPresent(transaction -> {
            transaction.setStatus(status);
            transaction.setCompletedAt(completedAt);
            transactionRepository.save(transaction);
        });
    }
}
