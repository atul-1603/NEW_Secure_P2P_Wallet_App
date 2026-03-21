package com.wallet.app.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.wallet.app.dto.CreateWithdrawalRequest;
import com.wallet.app.dto.WithdrawalHistoryItem;
import com.wallet.app.entity.BankAccount;
import com.wallet.app.entity.Transaction;
import com.wallet.app.entity.User;
import com.wallet.app.entity.Wallet;
import com.wallet.app.entity.WithdrawalRequest;
import com.wallet.app.repository.BankAccountRepository;
import com.wallet.app.repository.TransactionRepository;
import com.wallet.app.repository.UserRepository;
import com.wallet.app.repository.WalletRepository;
import com.wallet.app.repository.WithdrawalRequestRepository;

@Service
public class WithdrawalServiceImpl implements WithdrawalService {

    private static final List<String> BUSY_STATUSES = List.of("PENDING", "PROCESSING");

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final BankAccountRepository bankAccountRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final TransactionRepository transactionRepository;
    private final SensitiveDataEncryptionService encryptionService;
    private final WithdrawalProcessingService withdrawalProcessingService;

    public WithdrawalServiceImpl(
        UserRepository userRepository,
        WalletRepository walletRepository,
        BankAccountRepository bankAccountRepository,
        WithdrawalRequestRepository withdrawalRequestRepository,
        TransactionRepository transactionRepository,
        SensitiveDataEncryptionService encryptionService,
        WithdrawalProcessingService withdrawalProcessingService
    ) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.transactionRepository = transactionRepository;
        this.encryptionService = encryptionService;
        this.withdrawalProcessingService = withdrawalProcessingService;
    }

    @Override
    @Transactional
    public WithdrawalHistoryItem createWithdrawalRequest(String username, CreateWithdrawalRequest request) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));

        Wallet wallet = walletRepository.findByUserId(user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "wallet not found"));

        Wallet lockedWallet = walletRepository.findByIdForUpdateNoWait(wallet.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "wallet not found"));

        if (!"ACTIVE".equalsIgnoreCase(lockedWallet.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "wallet is not active");
        }

        BigDecimal amount = normalizeAmount(request.amount());
        if (lockedWallet.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "insufficient balance");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cooldownBoundary = now.minusSeconds(3);
        withdrawalRequestRepository
            .findTopByUserIdAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(user.getId(), BUSY_STATUSES, cooldownBoundary)
            .ifPresent(existing -> {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "withdrawal already in progress. please wait a few seconds");
            });

        BankAccount bankAccount = bankAccountRepository.findByIdAndUserId(request.bankAccountId(), user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "bank account not found"));

        lockedWallet.setBalance(lockedWallet.getBalance().subtract(amount));

        WithdrawalRequest withdrawal = new WithdrawalRequest();
        withdrawal.setUserId(user.getId());
        withdrawal.setAmount(amount);
        withdrawal.setStatus("PENDING");
        withdrawal.setBankAccountId(bankAccount.getId());
        withdrawal.setReferenceId(UUID.randomUUID());

        WithdrawalRequest saved = withdrawalRequestRepository.save(withdrawal);

        Transaction transaction = new Transaction();
        transaction.setFromWalletId(lockedWallet.getId());
        transaction.setToWalletId(null);
        transaction.setAmount(amount);
        transaction.setCurrency(lockedWallet.getCurrency());
        transaction.setTransactionType("WITHDRAWAL");
        transaction.setStatus("PENDING");
        transaction.setReference(saved.getReferenceId().toString());
        transaction.setNote("Withdrawal to " + bankAccount.getBankName());
        transactionRepository.save(transaction);

        scheduleProcessingAfterCommit(saved.getId());

        return toHistoryItem(saved, bankAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WithdrawalHistoryItem> getWithdrawalHistory(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));

        return withdrawalRequestRepository.findTop100ByUserIdOrderByCreatedAtDesc(user.getId())
            .stream()
            .map(withdrawal -> {
                BankAccount bankAccount = bankAccountRepository.findByIdAndUserId(withdrawal.getBankAccountId(), user.getId())
                    .orElse(null);
                return toHistoryItem(withdrawal, bankAccount);
            })
            .toList();
    }

    private void scheduleProcessingAfterCommit(UUID withdrawalId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            withdrawalProcessingService.processAsync(withdrawalId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                withdrawalProcessingService.processAsync(withdrawalId);
            }
        });
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount is required");
        }
        if (amount.scale() > 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount scale cannot exceed 4 decimals");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be greater than zero");
        }
        return amount;
    }

    private WithdrawalHistoryItem toHistoryItem(WithdrawalRequest withdrawal, BankAccount bankAccount) {
        if (bankAccount == null) {
            return new WithdrawalHistoryItem(
                withdrawal.getId(),
                withdrawal.getAmount(),
                withdrawal.getStatus(),
                withdrawal.getBankAccountId(),
                "Unknown Bank",
                "Unknown",
                "****",
                withdrawal.getReferenceId(),
                withdrawal.getCreatedAt(),
                withdrawal.getProcessedAt()
            );
        }

        String accountNumber = encryptionService.decrypt(bankAccount.getAccountNumber());

        return new WithdrawalHistoryItem(
            withdrawal.getId(),
            withdrawal.getAmount(),
            withdrawal.getStatus(),
            withdrawal.getBankAccountId(),
            bankAccount.getBankName(),
            bankAccount.getAccountHolderName(),
            maskAccountNumber(accountNumber),
            withdrawal.getReferenceId(),
            withdrawal.getCreatedAt(),
            withdrawal.getProcessedAt()
        );
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
