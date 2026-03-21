package com.wallet.app.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wallet.app.entity.UserPreferences;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {
}
