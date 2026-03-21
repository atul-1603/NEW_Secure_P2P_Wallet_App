package com.wallet.app.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.wallet.app.dto.TransactionHistoryItem;
import com.wallet.app.dto.TransferRequest;
import com.wallet.app.dto.TransferResponse;
import com.wallet.app.entity.NotificationType;
import com.wallet.app.entity.Transaction;
import com.wallet.app.entity.User;
import com.wallet.app.entity.Wallet;
import com.wallet.app.repository.TransactionRepository;
import com.wallet.app.repository.UserRepository;
import com.wallet.app.repository.WalletRepository;

@Service
public class TransferServiceImpl implements TransferService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;

    public TransferServiceImpl(
        WalletRepository walletRepository,
        UserRepository userRepository,
        TransactionRepository transactionRepository,
        NotificationService notificationService
    ) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public TransferResponse transfer(String username, TransferRequest request) {
        try {
            User sender = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));

            Wallet senderWallet = walletRepository.findByUserId(sender.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sender wallet not found"));

            UUID senderWalletId = senderWallet.getId();
            UUID receiverWalletId = resolveReceiverWalletId(sender, request);

            if (senderWalletId.equals(receiverWalletId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot transfer to same wallet");
            }

            BigDecimal amount = normalizeAmount(request.amount());

            Wallet firstLocked;
            Wallet secondLocked;
            boolean senderFirst = senderWalletId.compareTo(receiverWalletId) < 0;

            if (senderFirst) {
                firstLocked = walletRepository.findByIdForUpdateNoWait(senderWalletId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sender wallet not found"));
                secondLocked = walletRepository.findByIdForUpdateNoWait(receiverWalletId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "receiver wallet not found"));
            } else {
                firstLocked = walletRepository.findByIdForUpdateNoWait(receiverWalletId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "receiver wallet not found"));
                secondLocked = walletRepository.findByIdForUpdateNoWait(senderWalletId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sender wallet not found"));
            }

            Wallet lockedSender = senderFirst ? firstLocked : secondLocked;
            Wallet lockedReceiver = senderFirst ? secondLocked : firstLocked;

            ensureWalletIsActive(lockedSender, "sender wallet is not active");
            ensureWalletIsActive(lockedReceiver, "receiver wallet is not active");

            if (!lockedSender.getCurrency().equals(lockedReceiver.getCurrency())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currency mismatch between wallets");
            }

            if (lockedSender.getBalance().compareTo(amount) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "insufficient balance");
            }

            lockedSender.setBalance(lockedSender.getBalance().subtract(amount));
            lockedReceiver.setBalance(lockedReceiver.getBalance().add(amount));

            Transaction transaction = new Transaction();
            transaction.setFromWalletId(lockedSender.getId());
            transaction.setToWalletId(lockedReceiver.getId());
            transaction.setAmount(amount);
            transaction.setCurrency(lockedSender.getCurrency());
            transaction.setTransactionType("TRANSFER");
            transaction.setStatus("SUCCESS");
            transaction.setReference(normalizeReference(request.reference()));
            transaction.setNote(normalizeNote(request.note()));
            transaction.setCompletedAt(OffsetDateTime.now());

            Transaction savedTransaction = transactionRepository.save(transaction);

            User receiverUser = userRepository.findById(lockedReceiver.getUserId())
                .orElse(null);
            String receiverName = receiverUser == null ? "recipient" : receiverUser.getFullName();

            notificationService.createAndPublish(
                sender.getId(),
                NotificationType.DEBIT,
                "Money sent",
                "You sent INR " + amount + " to " + receiverName
            );

            notificationService.createAndPublish(
                lockedReceiver.getUserId(),
                NotificationType.CREDIT,
                "Money received",
                "You received INR " + amount + " from " + sender.getFullName()
            );

            return new TransferResponse(
                savedTransaction.getId(),
                lockedSender.getId(),
                lockedReceiver.getId(),
                savedTransaction.getAmount(),
                savedTransaction.getCurrency(),
                savedTransaction.getStatus(),
                savedTransaction.getReference(),
                lockedSender.getBalance(),
                lockedReceiver.getBalance(),
                savedTransaction.getCompletedAt()
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (PessimisticLockingFailureException exception) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "concurrent transfer in progress. please retry",
                exception
            );
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "transfer failed due to database error",
                exception
            );
        }
    }

    private UUID resolveReceiverWalletId(User sender, TransferRequest request) {
        UUID requestedWalletId = request.receiverWalletId();
        String receiverEmail = request.normalizedReceiverEmail();

        if (requestedWalletId == null && receiverEmail == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "receiverWalletId or receiverEmail is required");
        }

        Wallet walletFromEmail = null;
        if (receiverEmail != null) {
            if (receiverEmail.equalsIgnoreCase(sender.getEmail())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot transfer to self");
            }

            User receiverUser = userRepository.findByEmail(receiverEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "receiver user not found"));

            if (receiverUser.getId().equals(sender.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot transfer to self");
            }

            walletFromEmail = walletRepository.findByUserId(receiverUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "receiver wallet not found"));
        }

        if (requestedWalletId != null && walletFromEmail != null && !requestedWalletId.equals(walletFromEmail.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "receiverWalletId does not match receiverEmail");
        }

        if (walletFromEmail != null) {
            return walletFromEmail.getId();
        }

        return requestedWalletId;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionHistoryItem> getRecentTransactions(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));

        Wallet wallet = walletRepository.findByUserId(user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "wallet not found"));

        UUID walletId = wallet.getId();

        return transactionRepository
            .findTop100ByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(walletId, walletId)
            .stream()
            .map(this::toHistoryItem)
            .toList();
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

    private String normalizeReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return "TX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
        }
        return reference.trim();
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.trim();
    }

    private void ensureWalletIsActive(Wallet wallet, String message) {
        if (!"ACTIVE".equalsIgnoreCase(wallet.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private TransactionHistoryItem toHistoryItem(Transaction transaction) {
        return new TransactionHistoryItem(
            transaction.getId(),
            transaction.getFromWalletId(),
            transaction.getToWalletId(),
            transaction.getAmount(),
            transaction.getCurrency(),
            transaction.getTransactionType(),
            transaction.getStatus(),
            transaction.getReference(),
            transaction.getNote(),
            transaction.getCreatedAt(),
            transaction.getCompletedAt()
        );
    }
}
