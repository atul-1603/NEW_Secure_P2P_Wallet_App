package com.wallet.app.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wallet.app.entity.BankAccount;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    List<BankAccount> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<BankAccount> findByIdAndUserId(UUID id, UUID userId);
}
