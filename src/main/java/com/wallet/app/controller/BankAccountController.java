package com.wallet.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wallet.app.dto.BankAccountResponse;
import com.wallet.app.dto.CreateBankAccountRequest;
import com.wallet.app.service.BankAccountService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/bank-accounts")
public class BankAccountController {

    private final BankAccountService bankAccountService;

    public BankAccountController(BankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankAccountResponse addBankAccount(@Valid @RequestBody CreateBankAccountRequest request,
                                              Authentication authentication) {
        return bankAccountService.addBankAccount(authentication.getName(), request);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<BankAccountResponse> getBankAccounts(Authentication authentication) {
        return bankAccountService.getBankAccounts(authentication.getName());
    }
}
