package com.wallet.app.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.wallet.app.dto.ActiveSessionItemResponse;
import com.wallet.app.dto.ChangePasswordRequest;
import com.wallet.app.dto.MessageResponse;
import com.wallet.app.dto.ProfilePreferencesResponse;
import com.wallet.app.dto.SecuritySettingsResponse;
import com.wallet.app.dto.UpdateProfileRequest;
import com.wallet.app.dto.UpdateSecuritySettingsRequest;
import com.wallet.app.dto.UserProfileResponse;
import com.wallet.app.entity.NotificationType;
import com.wallet.app.entity.User;
import com.wallet.app.repository.UserRepository;

@Service
public class ProfileServiceImpl implements ProfileService {

    private static final long AVATAR_MAX_FILE_SIZE = 25 * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_CONTENT_TYPES = Set.of(
        "image/jpeg",
        "image/png"
    );
    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    public ProfileServiceImpl(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        FileStorageService fileStorageService,
        NotificationService notificationService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String username) {
        return toProfileResponse(getUserByUsername(username));
    }

    @Override
    @Transactional
    public UserProfileResponse updateMyProfile(String username, UpdateProfileRequest request) {
        User user = getUserByUsername(username);

        if (request.fullName() != null) {
            String normalizedFullName = request.fullName().trim();
            if (normalizedFullName.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "full name cannot be blank");
            }
            user.setFullName(normalizedFullName);
        }

        if (request.emailAlerts() != null) {
            user.setPreferenceEmailAlerts(request.emailAlerts());
        }

        if (request.transferAlerts() != null) {
            user.setPreferenceTransferAlerts(request.transferAlerts());
        }

        userRepository.save(Objects.requireNonNull(user));
        return toProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse uploadAvatar(String username, MultipartFile file) {
        User user = getUserByUsername(username);
        validateAvatarFile(file);

        FileStorageService.StoredFile storedAvatar = fileStorageService.store(
            file,
            "avatars",
            ALLOWED_AVATAR_CONTENT_TYPES,
            AVATAR_MAX_FILE_SIZE
        );

        user.setProfileImageUrl(storedAvatar.fileUrl());
        userRepository.save(Objects.requireNonNull(user));

        return toProfileResponse(user);
    }

    @Override
    @Transactional
    public MessageResponse changePassword(String username, ChangePasswordRequest request) {
        User user = getUserByUsername(username);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "new password must be different from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setLastPasswordChangedAt(OffsetDateTime.now());
        userRepository.save(Objects.requireNonNull(user));

        return new MessageResponse("password updated successfully");
    }

    @Override
    @Transactional
    public SecuritySettingsResponse updateSecuritySettings(String username, UpdateSecuritySettingsRequest request) {
        User user = getUserByUsername(username);
        user.setTwoFactorEnabled(request.twoFactorEnabled());

        userRepository.save(Objects.requireNonNull(user));

        notificationService.createAndPublish(
            user.getId(),
            NotificationType.SECURITY,
            "Security updated",
            "Two-factor authentication is now " + (user.isTwoFactorEnabled() ? "enabled" : "disabled")
        );

        return new SecuritySettingsResponse(
            user.isTwoFactorEnabled(),
            user.isEmailVerified(),
            user.getStatus()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveSessionItemResponse> getActiveSessions(String username, HttpServletRequest request) {
        User user = getUserByUsername(username);

        String userAgent = resolveUserAgent(request);
        String ipAddress = resolveIpAddress(request);

        ActiveSessionItemResponse currentSession = new ActiveSessionItemResponse(
            "session-" + user.getId().toString().replace("-", "").substring(0, 12),
            userAgent,
            ipAddress,
            OffsetDateTime.now(),
            true
        );

        return List.of(currentSession);
    }

    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatar file is required");
        }

        if (file.getSize() > AVATAR_MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatar file cannot exceed 25MB");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        if (!ALLOWED_AVATAR_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatar must be a JPG or PNG image");
        }
    }

    private String extractExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));
    }

    private String resolveIpAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        return userAgent;
    }

    private UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getEmail(),
            user.isEmailVerified(),
            user.getProfileImageUrl(),
            user.getStatus(),
            !"ACTIVE".equalsIgnoreCase(user.getStatus()),
            user.getKycStatus(),
            user.getKycDocumentType(),
            user.getKycDocumentUrl(),
            user.isTwoFactorEnabled(),
            user.getCreatedAt(),
            new ProfilePreferencesResponse(
                user.isPreferenceEmailAlerts(),
                user.isPreferenceTransferAlerts()
            )
        );
    }
}
