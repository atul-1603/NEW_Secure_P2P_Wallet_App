package com.wallet.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class WithdrawalServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private SensitiveDataEncryptionService encryptionService;

    @Mock
    private WithdrawalProcessingService withdrawalProcessingService;

    private WithdrawalServiceImpl withdrawalService;

    @BeforeEach
    void setUp() {
        withdrawalService = new WithdrawalServiceImpl(
            userRepository,
            walletRepository,
            bankAccountRepository,
            withdrawalRequestRepository,
            transactionRepository,
            encryptionService,
            withdrawalProcessingService
        );
    }

    @Test
    void createWithdrawalSuccessDeductsBalanceAndCreatesPendingTransaction() {
        User user = user("alice");
        Wallet wallet = wallet(user.getId(), new BigDecimal("500.0000"));
        BankAccount bankAccount = bankAccount(user.getId(), "ENCRYPTED");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(walletRepository.findByUserId(user.getId())).thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdForUpdateNoWait(wallet.getId())).thenReturn(Optional.of(wallet));
        when(withdrawalRequestRepository.findTopByUserIdAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
            any(), any(), any()
        )).thenReturn(Optional.empty());
        when(bankAccountRepository.findByIdAndUserId(bankAccount.getId(), user.getId())).thenReturn(Optional.of(bankAccount));
        when(withdrawalRequestRepository.save(any())).thenAnswer(invocation -> {
            WithdrawalRequest request = invocation.getArgument(0, WithdrawalRequest.class);
            request.setId(UUID.fromString("00000000-0000-0000-0000-000000001111"));
            request.setCreatedAt(OffsetDateTime.now());
            return request;
        });
        when(encryptionService.decrypt("ENCRYPTED")).thenReturn("123456789012");

        CreateWithdrawalRequest request = new CreateWithdrawalRequest(bankAccount.getId(), new BigDecimal("100.0000"));

        WithdrawalHistoryItem response = withdrawalService.createWithdrawalRequest("alice", request);

        assertEquals(new BigDecimal("400.0000"), wallet.getBalance());
        assertEquals("PENDING", response.status());
        assertEquals(new BigDecimal("100.0000"), response.amount());
        assertEquals("****9012", response.maskedAccountNumber());
        assertNotNull(response.referenceId());
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void createWithdrawalFailsForInsufficientBalance() {
        User user = user("alice");
        Wallet wallet = wallet(user.getId(), new BigDecimal("10.0000"));
        BankAccount bankAccount = bankAccount(user.getId(), "ENCRYPTED");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(walletRepository.findByUserId(user.getId())).thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdForUpdateNoWait(wallet.getId())).thenReturn(Optional.of(wallet));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            withdrawalService.createWithdrawalRequest(
                "alice",
                new CreateWithdrawalRequest(bankAccount.getId(), new BigDecimal("50.0000"))
            )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        String reason = exception.getReason();
        assertNotNull(reason);
        assertTrue(reason.contains("insufficient balance"));
    }

    @Test
    void createWithdrawalFailsForZeroAmount() {
        User user = user("alice");
        Wallet wallet = wallet(user.getId(), new BigDecimal("500.0000"));
        BankAccount bankAccount = bankAccount(user.getId(), "ENCRYPTED");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(walletRepository.findByUserId(user.getId())).thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdForUpdateNoWait(wallet.getId())).thenReturn(Optional.of(wallet));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            withdrawalService.createWithdrawalRequest(
                "alice",
                new CreateWithdrawalRequest(bankAccount.getId(), BigDecimal.ZERO)
            )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        String reason = exception.getReason();
        assertNotNull(reason);
        assertTrue(reason.contains("greater than zero"));
    }

    @Test
    void createWithdrawalFailsWhenAnotherPendingRequestExists() {
        User user = user("alice");
        Wallet wallet = wallet(user.getId(), new BigDecimal("500.0000"));
        BankAccount bankAccount = bankAccount(user.getId(), "ENCRYPTED");

        WithdrawalRequest pending = new WithdrawalRequest();
        pending.setId(UUID.randomUUID());
        pending.setUserId(user.getId());
        pending.setAmount(new BigDecimal("25.0000"));
        pending.setStatus("PENDING");
        pending.setBankAccountId(bankAccount.getId());
        pending.setReferenceId(UUID.randomUUID());
        pending.setCreatedAt(OffsetDateTime.now());

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(walletRepository.findByUserId(user.getId())).thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdForUpdateNoWait(wallet.getId())).thenReturn(Optional.of(wallet));
        when(withdrawalRequestRepository.findTopByUserIdAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
            any(), any(), any()
        )).thenReturn(Optional.of(pending));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            withdrawalService.createWithdrawalRequest(
                "alice",
                new CreateWithdrawalRequest(bankAccount.getId(), new BigDecimal("10.0000"))
            )
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
        String reason = exception.getReason();
        assertNotNull(reason);
        assertTrue(reason.contains("already in progress"));
    }

    @Test
    void historyReturnsMappedItems() {
        User user = user("alice");
        BankAccount bankAccount = bankAccount(user.getId(), "ENCRYPTED");
        WithdrawalRequest request = new WithdrawalRequest();
        request.setId(UUID.randomUUID());
        request.setUserId(user.getId());
        request.setAmount(new BigDecimal("10.0000"));
        request.setStatus("SUCCESS");
        request.setBankAccountId(bankAccount.getId());
        request.setReferenceId(UUID.randomUUID());
        request.setCreatedAt(OffsetDateTime.now().minusMinutes(1));
        request.setProcessedAt(OffsetDateTime.now());

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(withdrawalRequestRepository.findTop100ByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of(request));
        when(bankAccountRepository.findByIdAndUserId(bankAccount.getId(), user.getId())).thenReturn(Optional.of(bankAccount));
        when(encryptionService.decrypt("ENCRYPTED")).thenReturn("987654321001");

        List<WithdrawalHistoryItem> history = withdrawalService.getWithdrawalHistory("alice");

        assertEquals(1, history.size());
        assertEquals("SUCCESS", history.get(0).status());
        assertEquals("****1001", history.get(0).maskedAccountNumber());
    }

    private User user(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setStatus("ACTIVE");
        return user;
    }

    private Wallet wallet(UUID userId, BigDecimal balance) {
        Wallet wallet = new Wallet();
        wallet.setId(UUID.randomUUID());
        wallet.setUserId(userId);
        wallet.setBalance(balance);
        wallet.setCurrency("INR");
        wallet.setStatus("ACTIVE");
        return wallet;
    }

    private BankAccount bankAccount(UUID userId, String encryptedAccountNumber) {
        BankAccount account = new BankAccount();
        account.setId(UUID.randomUUID());
        account.setUserId(userId);
        account.setAccountHolderName("Alice");
        account.setAccountNumber(encryptedAccountNumber);
        account.setIfscCode("HDFC0001234");
        account.setBankName("HDFC Bank");
        account.setVerified(false);
        return account;
    }
}
