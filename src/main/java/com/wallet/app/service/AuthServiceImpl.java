package com.wallet.app.service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.wallet.app.dto.AuthResponse;
import com.wallet.app.dto.LoginRequest;
import com.wallet.app.dto.LoginResponse;
import com.wallet.app.dto.MessageResponse;
import com.wallet.app.dto.ResendEmailVerificationRequest;
import com.wallet.app.dto.RegisterRequest;
import com.wallet.app.dto.VerifyEmailRequest;
import com.wallet.app.dto.VerifyOtpRequest;
import com.wallet.app.entity.NotificationType;
import com.wallet.app.entity.User;
import com.wallet.app.repository.UserRepository;
import com.wallet.app.security.JwtTokenProvider;

@Service
public class AuthServiceImpl implements AuthService {

    private static final int OTP_VALIDITY_MINUTES = 5;
    private static final int OTP_VALIDITY_SECONDS = OTP_VALIDITY_MINUTES * 60;
    private static final int OTP_UPPER_BOUND = 1_000_000;
    private static final Pattern USERNAME_SEPARATOR_PATTERN = Pattern.compile("[_\\-.]+");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final WalletService walletService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final boolean otpEnabled;
    private final SecureRandom secureRandom;

    public AuthServiceImpl(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider jwtTokenProvider,
        WalletService walletService,
        EmailService emailService,
        NotificationService notificationService,
        @Value("${security.auth.otp-enabled:true}") boolean otpEnabled
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.walletService = walletService;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.otpEnabled = otpEnabled;
        this.secureRandom = new SecureRandom();
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedUsername = request.username().trim();
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username already exists");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already exists");
        }

        User user = new User();
        user.setUsername(normalizedUsername);
        user.setFullName(buildDefaultFullName(normalizedUsername));
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setTwoFactorEnabled(true);
        if (otpEnabled) {
            user.setStatus("PENDING_VERIFICATION");
            user.setEmailVerified(false);
            assignNewOtp(user);
        } else {
            user.setStatus("ACTIVE");
            user.setEmailVerified(true);
            clearOtp(user);
        }

        User savedUser = userRepository.save(user);
        walletService.createDefaultWalletForUser(savedUser.getId());

        if (otpEnabled) {
            emailService.sendEmailVerificationOtp(savedUser.getEmail(), savedUser.getOtpCode(), OTP_VALIDITY_MINUTES);
        }

        return new AuthResponse(
            otpEnabled ? "registration successful. verify your email using the otp sent" : "registration successful",
            savedUser.getId(),
            savedUser.getUsername(),
            savedUser.getEmail(),
            savedUser.getStatus()
        );
    }

    @Override
    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (!otpEnabled) {
            User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "account not found"));

            return new AuthResponse(
                "email verification is disabled",
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus()
            );
        }

        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid email or otp"));

        if (user.isEmailVerified()) {
            return new AuthResponse(
                "email already verified",
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus()
            );
        }

        validateOtp(user, request.otpCode());

        user.setEmailVerified(true);
        user.setStatus("ACTIVE");
        clearOtp(user);
        User savedUser = userRepository.save(user);

        return new AuthResponse(
            "email verified successfully",
            savedUser.getId(),
            savedUser.getUsername(),
            savedUser.getEmail(),
            savedUser.getStatus()
        );
    }

    @Override
    @Transactional
    public MessageResponse resendEmailVerificationOtp(ResendEmailVerificationRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (!otpEnabled) {
            return new MessageResponse("email verification otp is disabled");
        }

        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "account not found"));

        if (user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "account already verified");
        }

        assignNewOtp(user);
        userRepository.save(user);
        emailService.sendEmailVerificationOtp(user.getEmail(), user.getOtpCode(), OTP_VALIDITY_MINUTES);

        return new MessageResponse("verification otp sent");
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String identifier = request.usernameOrEmail().trim();

        User user = userRepository.findByUsernameOrEmail(identifier, identifier.toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }

        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "account not verified. verify your email first");
        }

        if (!otpEnabled || !user.isTwoFactorEnabled()) {
            String accessToken = jwtTokenProvider.generateAccessToken(user);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user);

            notificationService.createAndPublish(
                user.getId(),
                NotificationType.LOGIN,
                "Login detected",
                "Your account was signed in successfully"
            );

            return new LoginResponse(
                "login successful",
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus(),
                false,
                0,
                "Bearer",
                accessToken,
                jwtTokenProvider.getAccessTokenExpirationMs(),
                refreshToken,
                jwtTokenProvider.getRefreshTokenExpirationMs()
            );
        }

        assignNewOtp(user);
        userRepository.save(user);
        emailService.sendLoginOtp(user.getEmail(), user.getOtpCode(), OTP_VALIDITY_MINUTES);

        return new LoginResponse(
            "otp sent to your email",
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getStatus(),
            true,
            OTP_VALIDITY_SECONDS,
            null,
            null,
            0,
            null,
            0
        );
    }

    @Override
    @Transactional
    public LoginResponse verifyLoginOtp(VerifyOtpRequest request) {
        if (!otpEnabled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "otp verification is disabled. login directly");
        }

        String identifier = request.usernameOrEmail().trim();

        User user = userRepository.findByUsernameOrEmail(identifier, identifier.toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));

        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "account not verified. verify your email first");
        }

        validateOtp(user, request.otpCode());
        clearOtp(user);
        userRepository.save(user);

        notificationService.createAndPublish(
            user.getId(),
            NotificationType.LOGIN,
            "Login verified",
            "OTP verified and login completed successfully"
        );

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        return new LoginResponse(
            "login successful",
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getStatus(),
            false,
            0,
            "Bearer",
            accessToken,
            jwtTokenProvider.getAccessTokenExpirationMs(),
            refreshToken,
            jwtTokenProvider.getRefreshTokenExpirationMs()
        );
    }

    private void assignNewOtp(User user) {
        user.setOtpCode(generateOtpCode());
        user.setOtpExpiry(OffsetDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES));
    }

    private String generateOtpCode() {
        int otpValue = secureRandom.nextInt(OTP_UPPER_BOUND);
        return String.format("%06d", otpValue);
    }

    private void validateOtp(User user, String submittedOtpCode) {
        if (user.getOtpCode() == null || user.getOtpExpiry() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "otp not found. request a new otp");
        }

        if (!user.getOtpExpiry().isAfter(OffsetDateTime.now())) {
            clearOtp(user);
            userRepository.save(user);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "otp expired. request a new otp");
        }

        if (!Objects.equals(user.getOtpCode(), submittedOtpCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "incorrect otp");
        }
    }

    private void clearOtp(User user) {
        user.setOtpCode(null);
        user.setOtpExpiry(null);
    }

    private String buildDefaultFullName(String username) {
        String sanitized = USERNAME_SEPARATOR_PATTERN.matcher(username.trim()).replaceAll(" ").trim();
        if (sanitized.isBlank()) {
            return "Wallet User";
        }

        String[] parts = sanitized.split("\\s+");
        StringBuilder output = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (output.length() > 0) {
                output.append(' ');
            }

            output.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                output.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }

        return output.toString();
    }
}
