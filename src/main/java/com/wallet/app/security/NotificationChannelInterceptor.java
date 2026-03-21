package com.wallet.app.security;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class NotificationChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final String USER_ID_SESSION_KEY = "ws.userId";

    private final JwtTokenProvider jwtTokenProvider;

    public NotificationChannelInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            String token = extractBearerToken(accessor);
            if (!jwtTokenProvider.validateAccessToken(token)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid websocket access token");
            }

            UUID userId = jwtTokenProvider.getUserId(token);
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "websocket session missing auth context");
            }
            sessionAttributes.put(USER_ID_SESSION_KEY, userId.toString());
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            String destination = accessor.getDestination();
            if (destination == null || !destination.startsWith("/topic/notifications/")) {
                return message;
            }

            String subscribedUserId = destination.substring("/topic/notifications/".length());
            String sessionUserId = getSessionUserId(accessor.getSessionAttributes());
            if (!subscribedUserId.equals(sessionUserId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cannot subscribe to another user's notifications");
            }
        }

        return message;
    }

    private String extractBearerToken(StompHeaderAccessor accessor) {
        List<String> authorizationHeaders = accessor.getNativeHeader(AUTHORIZATION);
        if (authorizationHeaders == null || authorizationHeaders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing websocket authorization header");
        }

        String headerValue = authorizationHeaders.get(0);
        if (headerValue == null || !headerValue.startsWith(BEARER)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid websocket authorization header");
        }

        return headerValue.substring(BEARER.length());
    }

    private String getSessionUserId(Map<String, Object> sessionAttributes) {
        if (sessionAttributes == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "websocket session missing auth context");
        }

        Object value = sessionAttributes.get(USER_ID_SESSION_KEY);
        if (!(value instanceof String userId) || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "websocket session missing user identity");
        }

        return Objects.requireNonNull(userId);
    }
}
