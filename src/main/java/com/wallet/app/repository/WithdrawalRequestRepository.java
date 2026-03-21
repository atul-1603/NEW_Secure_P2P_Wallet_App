package com.wallet.app.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wallet.app.entity.WithdrawalRequest;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {

    List<WithdrawalRequest> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<WithdrawalRequest> findTopByUserIdAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
        UUID userId,
        List<String> statuses,
        OffsetDateTime createdAfter
    );
}
