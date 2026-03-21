package com.wallet.app.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wallet.app.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

	List<Transaction> findTop100ByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(UUID fromWalletId, UUID toWalletId);

    Optional<Transaction> findByReference(String reference);
}
