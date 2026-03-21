package com.wallet.app.service;

import java.util.List;

import com.wallet.app.dto.CreateWithdrawalRequest;
import com.wallet.app.dto.WithdrawalHistoryItem;

public interface WithdrawalService {

    WithdrawalHistoryItem createWithdrawalRequest(String username, CreateWithdrawalRequest request);

    List<WithdrawalHistoryItem> getWithdrawalHistory(String username);
}
