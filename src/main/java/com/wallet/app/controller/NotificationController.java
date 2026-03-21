package com.wallet.app.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wallet.app.dto.NotificationsResponse;
import com.wallet.app.service.NotificationService;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public NotificationsResponse getMyNotifications(Authentication authentication) {
        return notificationService.getForCurrentUser(authentication.getName());
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.OK)
    public void markAsRead(@PathVariable("id") UUID notificationId, Authentication authentication) {
        notificationService.markAsRead(authentication.getName(), notificationId);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.OK)
    public void markAllAsRead(Authentication authentication) {
        notificationService.markAllAsRead(authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOne(@PathVariable("id") UUID notificationId, Authentication authentication) {
        notificationService.deleteNotification(authentication.getName(), notificationId);
    }
}
