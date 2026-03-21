package com.wallet.app.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.wallet.app.dto.NotificationResponse;
import com.wallet.app.dto.NotificationsResponse;
import com.wallet.app.entity.Notification;
import com.wallet.app.entity.NotificationType;
import com.wallet.app.entity.User;
import com.wallet.app.entity.UserPreferences;
import com.wallet.app.repository.NotificationRepository;
import com.wallet.app.repository.UserRepository;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserPreferencesService userPreferencesService;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationServiceImpl(
        NotificationRepository notificationRepository,
        UserRepository userRepository,
        UserPreferencesService userPreferencesService,
        SimpMessagingTemplate messagingTemplate
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.userPreferencesService = userPreferencesService;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationsResponse getForCurrentUser(String username) {
        User user = findUserByUsername(username);
        List<NotificationResponse> notifications = notificationRepository
            .findTop200ByUserIdOrderByCreatedAtDesc(user.getId())
            .stream()
            .map(this::toResponse)
            .toList();

        long unreadCount = notificationRepository.countByUserIdAndReadFalse(user.getId());
        return new NotificationsResponse(unreadCount, notifications);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(String username, UUID notificationId) {
        User user = findUserByUsername(username);

        Notification notification = notificationRepository.findByIdAndUserId(notificationId, user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "notification not found"));

        notification.setRead(true);
        Notification saved = notificationRepository.save(notification);
        NotificationResponse response = toResponse(saved);
        publish(saved.getUserId(), response);
        return response;
    }

    @Override
    @Transactional
    public List<NotificationResponse> markAllAsRead(String username) {
        User user = findUserByUsername(username);

        List<NotificationResponse> updated = notificationRepository.findTop200ByUserIdOrderByCreatedAtDesc(user.getId())
            .stream()
            .peek(item -> item.setRead(true))
            .map(notificationRepository::save)
            .map(this::toResponse)
            .toList();

        publishAllRead(user.getId());
        return updated;
    }

    @Override
    @Transactional
    public void deleteNotification(String username, UUID notificationId) {
        User user = findUserByUsername(username);

        Notification notification = notificationRepository.findByIdAndUserId(notificationId, user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "notification not found"));

        notificationRepository.delete(Objects.requireNonNull(notification));
        publishDeleted(user.getId(), notificationId);
    }

    @Override
    @Transactional
    public NotificationResponse createAndPublish(UUID userId, NotificationType type, String title, String message) {
        if (!isEnabledByPreference(userId, type)) {
            return null;
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRead(false);

        Notification saved = notificationRepository.save(notification);
        NotificationResponse response = toResponse(saved);
        publish(userId, response);
        return response;
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getType().name(),
            notification.getTitle(),
            notification.getMessage(),
            notification.isRead(),
            notification.getCreatedAt()
        );
    }

    private boolean isEnabledByPreference(UUID userId, NotificationType type) {
        UserPreferences preferences = userPreferencesService.getOrCreateByUserId(userId);

        return switch (type) {
            case CREDIT, DEBIT, WITHDRAWAL -> preferences.isTransactionNotifications();
            case LOGIN, SECURITY -> preferences.isSecurityAlerts();
            case SYSTEM -> true;
        };
    }

    private void publish(UUID userId, NotificationResponse payload) {
        messagingTemplate.convertAndSend("/topic/notifications/" + userId, new NotificationSocketEvent("UPSERT", payload, null));
    }

    private void publishDeleted(UUID userId, UUID notificationId) {
        messagingTemplate.convertAndSend("/topic/notifications/" + userId, new NotificationSocketEvent("DELETE", null, notificationId));
    }

    private void publishAllRead(UUID userId) {
        messagingTemplate.convertAndSend("/topic/notifications/" + userId, new NotificationSocketEvent("ALL_READ", null, null));
    }

    public record NotificationSocketEvent(
        String action,
        NotificationResponse notification,
        UUID notificationId
    ) {
    }
}
