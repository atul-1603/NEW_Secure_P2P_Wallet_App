package com.wallet.app.service;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.wallet.app.dto.UpdateUserPreferencesRequest;
import com.wallet.app.dto.UserPreferencesResponse;
import com.wallet.app.entity.User;
import com.wallet.app.entity.UserPreferences;
import com.wallet.app.repository.UserPreferencesRepository;
import com.wallet.app.repository.UserRepository;

@Service
public class UserPreferencesServiceImpl implements UserPreferencesService {

    private final UserPreferencesRepository userPreferencesRepository;
    private final UserRepository userRepository;

    public UserPreferencesServiceImpl(
        UserPreferencesRepository userPreferencesRepository,
        UserRepository userRepository
    ) {
        this.userPreferencesRepository = userPreferencesRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserPreferencesResponse getForCurrentUser(String username) {
        User user = findUserByUsername(username);
        UserPreferences preferences = getOrCreateByUserId(user.getId());
        return toResponse(preferences);
    }

    @Override
    @Transactional
    public UserPreferencesResponse updateForCurrentUser(String username, UpdateUserPreferencesRequest request) {
        User user = findUserByUsername(username);
        UserPreferences preferences = getOrCreateByUserId(user.getId());

        preferences.setEmailNotifications(request.emailNotifications());
        preferences.setTransactionNotifications(request.transactionNotifications());
        preferences.setSecurityAlerts(request.securityAlerts());

        UserPreferences saved = userPreferencesRepository.save(preferences);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public UserPreferences getOrCreateByUserId(UUID userId) {
        UUID nonNullUserId = Objects.requireNonNull(userId, "userId is required");
        return userPreferencesRepository.findById(nonNullUserId)
            .orElseGet(() -> {
                UserPreferences preferences = new UserPreferences();
                preferences.setUserId(nonNullUserId);
                preferences.setEmailNotifications(true);
                preferences.setTransactionNotifications(true);
                preferences.setSecurityAlerts(true);
                return userPreferencesRepository.save(preferences);
            });
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));
    }

    private UserPreferencesResponse toResponse(UserPreferences preferences) {
        OffsetDateTime updatedAt = preferences.getUpdatedAt() == null ? OffsetDateTime.now() : preferences.getUpdatedAt();
        return new UserPreferencesResponse(
            preferences.isEmailNotifications(),
            preferences.isTransactionNotifications(),
            preferences.isSecurityAlerts(),
            updatedAt
        );
    }
}
