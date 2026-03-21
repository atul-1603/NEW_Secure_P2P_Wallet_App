package com.wallet.app.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @NotNull
    @Column(name = "email_notifications", nullable = false)
    private boolean emailNotifications = true;

    @NotNull
    @Column(name = "transaction_notifications", nullable = false)
    private boolean transactionNotifications = true;

    @NotNull
    @Column(name = "security_alerts", nullable = false)
    private boolean securityAlerts = true;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UserPreferences() {
    }

    @PrePersist
    void ensureDefaults() {
        if (userId == null) {
            throw new IllegalStateException("userId is required");
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public boolean isEmailNotifications() {
        return emailNotifications;
    }

    public void setEmailNotifications(boolean emailNotifications) {
        this.emailNotifications = emailNotifications;
    }

    public boolean isTransactionNotifications() {
        return transactionNotifications;
    }

    public void setTransactionNotifications(boolean transactionNotifications) {
        this.transactionNotifications = transactionNotifications;
    }

    public boolean isSecurityAlerts() {
        return securityAlerts;
    }

    public void setSecurityAlerts(boolean securityAlerts) {
        this.securityAlerts = securityAlerts;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
