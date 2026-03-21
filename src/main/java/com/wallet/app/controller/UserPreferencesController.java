package com.wallet.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wallet.app.dto.UpdateUserPreferencesRequest;
import com.wallet.app.dto.UserPreferencesResponse;
import com.wallet.app.service.UserPreferencesService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/preferences")
public class UserPreferencesController {

    private final UserPreferencesService userPreferencesService;

    public UserPreferencesController(UserPreferencesService userPreferencesService) {
        this.userPreferencesService = userPreferencesService;
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public UserPreferencesResponse getPreferences(Authentication authentication) {
        return userPreferencesService.getForCurrentUser(authentication.getName());
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public UserPreferencesResponse updatePreferences(@Valid @RequestBody UpdateUserPreferencesRequest request,
                                                     Authentication authentication) {
        return userPreferencesService.updateForCurrentUser(authentication.getName(), request);
    }
}
