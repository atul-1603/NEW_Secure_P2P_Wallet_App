package com.wallet.app.service;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.wallet.app.dto.BankAccountResponse;
import com.wallet.app.dto.CreateBankAccountRequest;
import com.wallet.app.entity.BankAccount;
import com.wallet.app.entity.User;
import com.wallet.app.repository.BankAccountRepository;
import com.wallet.app.repository.UserRepository;

@Service
public class BankAccountServiceImpl implements BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final UserRepository userRepository;
    private final SensitiveDataEncryptionService encryptionService;

    public BankAccountServiceImpl(
        BankAccountRepository bankAccountRepository,
        UserRepository userRepository,
        SensitiveDataEncryptionService encryptionService
    ) {
        this.bankAccountRepository = bankAccountRepository;
        this.userRepository = userRepository;
        this.encryptionService = encryptionService;
    }

    @Override
    @Transactional
    public BankAccountResponse addBankAccount(String username, CreateBankAccountRequest request) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));

        BankAccount account = new BankAccount();
        account.setUserId(user.getId());
        account.setAccountHolderName(normalizeText(request.accountHolderName()));
        account.setAccountNumber(encryptionService.encrypt(normalizeAccountNumber(request.accountNumber())));
        account.setIfscCode(normalizeIfsc(request.ifscCode()));
        account.setBankName(normalizeText(request.bankName()));
        account.setVerified(false);

        BankAccount saved = bankAccountRepository.save(account);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BankAccountResponse> getBankAccounts(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));

        return bankAccountRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "field is required");
        }
        return value.trim();
    }

    private String normalizeIfsc(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ifscCode is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[A-Z]{4}0[A-Z0-9]{6}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ifscCode must be a valid IFSC");
        }
        return normalized;
    }

    private String normalizeAccountNumber(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountNumber is required");
        }
        String normalized = value.trim();
        if (!normalized.matches("^[0-9]{9,18}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountNumber must be 9 to 18 digits");
        }
        return normalized;
    }

    private BankAccountResponse toResponse(BankAccount account) {
        String decryptedAccountNumber = encryptionService.decrypt(account.getAccountNumber());
        return new BankAccountResponse(
            account.getId(),
            account.getAccountHolderName(),
            maskAccountNumber(decryptedAccountNumber),
            account.getIfscCode(),
            account.getBankName(),
            Boolean.TRUE.equals(account.getVerified()),
            account.getCreatedAt()
        );
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber.length() <= 4) {
            return "****";
        }
        String last4 = accountNumber.substring(accountNumber.length() - 4);
        return "****" + last4;
    }
}
