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

import com.wallet.app.dto.CreateWithdrawalRequest;
import com.wallet.app.dto.WithdrawalHistoryItem;
import com.wallet.app.service.WithdrawalService;

import jakarta.validation.Valid;

@RestController
@RequestMapping
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    public WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.OK)
    public WithdrawalHistoryItem createWithdrawal(@Valid @RequestBody CreateWithdrawalRequest request,
                                                  Authentication authentication) {
        return withdrawalService.createWithdrawalRequest(authentication.getName(), request);
    }

    @GetMapping("/withdrawals")
    @ResponseStatus(HttpStatus.OK)
    public List<WithdrawalHistoryItem> getWithdrawalHistory(Authentication authentication) {
        return withdrawalService.getWithdrawalHistory(authentication.getName());
    }
}
