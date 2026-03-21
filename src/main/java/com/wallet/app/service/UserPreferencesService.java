package com.wallet.app.service;

import java.util.UUID;

import com.wallet.app.dto.UpdateUserPreferencesRequest;
import com.wallet.app.dto.UserPreferencesResponse;
import com.wallet.app.entity.UserPreferences;

public interface UserPreferencesService {

    UserPreferencesResponse getForCurrentUser(String username);

    UserPreferencesResponse updateForCurrentUser(String username, UpdateUserPreferencesRequest request);

    UserPreferences getOrCreateByUserId(UUID userId);
}
