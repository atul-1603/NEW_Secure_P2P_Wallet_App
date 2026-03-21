package com.wallet.app.service;

import java.util.List;

import com.wallet.app.dto.BankAccountResponse;
import com.wallet.app.dto.CreateBankAccountRequest;

public interface BankAccountService {

    BankAccountResponse addBankAccount(String username, CreateBankAccountRequest request);

    List<BankAccountResponse> getBankAccounts(String username);
}
