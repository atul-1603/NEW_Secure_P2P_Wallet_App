package com.wallet.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.wallet.app.dto.TransferRequest;
import com.wallet.app.dto.TransferResponse;
import com.wallet.app.entity.Transaction;
import com.wallet.app.entity.User;
import com.wallet.app.entity.Wallet;
import com.wallet.app.repository.TransactionRepository;
import com.wallet.app.repository.UserRepository;
import com.wallet.app.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class TransferServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private NotificationService notificationService;

    private TransferServiceImpl transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferServiceImpl(walletRepository, userRepository, transactionRepository, notificationService);
    }

    @Test
    void transferSuccessMovesFundsAtomicallyAndStoresSuccessTransaction() {
        UUID senderUserId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID senderWalletId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID receiverWalletId = UUID.fromString("00000000-0000-0000-0000-000000000202");

        User sender = user("alice", senderUserId);
        Wallet senderWallet = wallet(senderWalletId, senderUserId, new BigDecimal("100.0000"));
        Wallet receiverWallet = wallet(receiverWalletId, UUID.fromString("00000000-0000-0000-0000-000000000022"), new BigDecimal("20.0000"));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(walletRepository.findByUserId(senderUserId)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdateNoWait(senderWalletId)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdateNoWait(receiverWalletId)).thenReturn(Optional.of(receiverWallet));
        AtomicReference<Transaction> savedTransaction = new AtomicReference<>();
        when(transactionRepository.save(notNull())).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0, Transaction.class);
            savedTransaction.set(tx);
            tx.setId(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"));
            return tx;
        });

        TransferRequest request = new TransferRequest(receiverWalletId, null, new BigDecimal("50.0000"), null, "rent");

        TransferResponse response = transferService.transfer("alice", request);

        assertEquals(new BigDecimal("50.0000"), response.senderBalance());
        assertEquals(new BigDecimal("70.0000"), response.receiverBalance());
        assertEquals("SUCCESS", response.status());
        assertEquals(receiverWalletId, response.toWalletId());
        assertNotNull(savedTransaction.get());
        assertEquals("SUCCESS", savedTransaction.get().getStatus());
        assertEquals("TRANSFER", savedTransaction.get().getTransactionType());
    }

    @Test
    void transferFailsForInsufficientBalance() {
        UUID senderUserId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID senderWalletId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID receiverWalletId = UUID.fromString("00000000-0000-0000-0000-000000000202");

        User sender = user("alice", senderUserId);
        Wallet senderWallet = wallet(senderWalletId, senderUserId, new BigDecimal("10.0000"));
        Wallet receiverWallet = wallet(receiverWalletId, UUID.fromString("00000000-0000-0000-0000-000000000022"), new BigDecimal("20.0000"));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(walletRepository.findByUserId(senderUserId)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdateNoWait(senderWalletId)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdateNoWait(receiverWalletId)).thenReturn(Optional.of(receiverWallet));

        TransferRequest request = new TransferRequest(receiverWalletId, null, new BigDecimal("50.0000"), null, null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            transferService.transfer("alice", request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        String reason = exception.getReason();
        assertNotNull(reason);
        assertTrue(reason.contains("insufficient balance"));
    }

    @Test
    void transferFailsWhenSenderAndReceiverWalletAreSame() {
        UUID senderUserId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID senderWalletId = UUID.fromString("00000000-0000-0000-0000-000000000101");

        User sender = user("alice", senderUserId);
        Wallet senderWallet = wallet(senderWalletId, senderUserId, new BigDecimal("100.0000"));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(walletRepository.findByUserId(senderUserId)).thenReturn(Optional.of(senderWallet));

        TransferRequest request = new TransferRequest(senderWalletId, null, new BigDecimal("1.0000"), null, null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            transferService.transfer("alice", request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        String reason = exception.getReason();
        assertNotNull(reason);
        assertTrue(reason.contains("same wallet"));
    }

    @Test
    void transferFailsForInvalidReceiverWallet() {
        UUID senderUserId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID senderWalletId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID receiverWalletId = UUID.fromString("00000000-0000-0000-0000-000000000202");

        User sender = user("alice", senderUserId);
        Wallet senderWallet = wallet(senderWalletId, senderUserId, new BigDecimal("100.0000"));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(walletRepository.findByUserId(senderUserId)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdateNoWait(senderWalletId)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdateNoWait(receiverWalletId)).thenReturn(Optional.empty());

        TransferRequest request = new TransferRequest(receiverWalletId, null, new BigDecimal("1.0000"), null, null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            transferService.transfer("alice", request)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        String reason = exception.getReason();
        assertNotNull(reason);
        assertTrue(reason.contains("receiver wallet not found"));
    }

    @Test
    void transferReturnsConflictWhenConcurrentLockCannotBeAcquired() {
        UUID senderUserId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID senderWalletId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID receiverWalletId = UUID.fromString("00000000-0000-0000-0000-000000000202");

        User sender = user("alice", senderUserId);
        Wallet senderWallet = wallet(senderWalletId, senderUserId, new BigDecimal("100.0000"));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(walletRepository.findByUserId(senderUserId)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdateNoWait(senderWalletId))
            .thenThrow(new CannotAcquireLockException("wallet row is locked"));

        TransferRequest request = new TransferRequest(receiverWalletId, null, new BigDecimal("1.0000"), null, null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            transferService.transfer("alice", request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        String reason = exception.getReason();
        assertNotNull(reason);
        assertTrue(reason.contains("concurrent transfer in progress"));
    }

    @Test
    void transferResolvesReceiverByEmailWhenWalletIdIsNotProvided() {
        UUID senderUserId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID receiverUserId = UUID.fromString("00000000-0000-0000-0000-000000000022");
        UUID senderWalletId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID receiverWalletId = UUID.fromString("00000000-0000-0000-0000-000000000202");

        User sender = user("alice", senderUserId);
        User receiver = user("bob", receiverUserId);
        Wallet senderWallet = wallet(senderWalletId, senderUserId, new BigDecimal("100.0000"));
        Wallet receiverWallet = wallet(receiverWalletId, receiverUserId, new BigDecimal("20.0000"));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(walletRepository.findByUserId(senderUserId)).thenReturn(Optional.of(senderWallet));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(receiver));
        when(walletRepository.findByUserId(receiverUserId)).thenReturn(Optional.of(receiverWallet));
        when(walletRepository.findByIdForUpdateNoWait(senderWalletId)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdateNoWait(receiverWalletId)).thenReturn(Optional.of(receiverWallet));
        when(transactionRepository.save(notNull())).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0, Transaction.class);
            tx.setId(UUID.fromString("00000000-0000-0000-0000-00000000bbbb"));
            return tx;
        });

        TransferRequest request = new TransferRequest(null, "bob@example.com", new BigDecimal("10.0000"), null, null);

        TransferResponse response = transferService.transfer("alice", request);

        assertEquals(receiverWalletId, response.toWalletId());
        assertEquals(new BigDecimal("90.0000"), response.senderBalance());
        assertEquals(new BigDecimal("30.0000"), response.receiverBalance());
    }

    private User user(String username, UUID id) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setStatus("ACTIVE");
        return user;
    }

    private Wallet wallet(UUID walletId, UUID userId, BigDecimal balance) {
        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setUserId(userId);
        wallet.setBalance(balance);
        wallet.setCurrency("USD");
        wallet.setStatus("ACTIVE");
        return wallet;
    }
}
