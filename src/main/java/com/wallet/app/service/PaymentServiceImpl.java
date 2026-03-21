package com.wallet.app.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.wallet.app.dto.CreatePaymentOrderRequest;
import com.wallet.app.dto.CreatePaymentOrderResponse;
import com.wallet.app.dto.VerifyPaymentRequest;
import com.wallet.app.dto.VerifyPaymentResponse;
import com.wallet.app.entity.NotificationType;
import com.wallet.app.entity.Payment;
import com.wallet.app.entity.Transaction;
import com.wallet.app.entity.User;
import com.wallet.app.entity.Wallet;
import com.wallet.app.repository.PaymentRepository;
import com.wallet.app.repository.TransactionRepository;
import com.wallet.app.repository.UserRepository;
import com.wallet.app.repository.WalletRepository;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;
    private final String razorpayKeyId;
    private final String razorpayKeySecret;

    public PaymentServiceImpl(
        PaymentRepository paymentRepository,
        UserRepository userRepository,
        WalletRepository walletRepository,
        TransactionRepository transactionRepository,
        NotificationService notificationService,
        @Value("${payment.razorpay.key-id:}") String razorpayKeyId,
        @Value("${payment.razorpay.key-secret:}") String razorpayKeySecret
    ) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.notificationService = notificationService;
        this.razorpayKeyId = razorpayKeyId;
        this.razorpayKeySecret = razorpayKeySecret;
    }

    @Override
    @Transactional
    public CreatePaymentOrderResponse createOrder(String username, CreatePaymentOrderRequest request) {
        ensureRazorpayConfig();

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));

        Wallet wallet = walletRepository.findByUserId(user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "wallet not found"));

        if (!"ACTIVE".equalsIgnoreCase(wallet.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "wallet is not active");
        }

        BigDecimal amount = normalizeAmount(request.amount());
        long amountInPaise = toPaise(amount);

        String orderId = createRazorpayOrder(amountInPaise, user.getId());

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setUserId(user.getId());
        payment.setAmount(amount);
        payment.setStatus("PENDING");

        paymentRepository.save(payment);

        return new CreatePaymentOrderResponse(orderId, amount, normalizedCurrency(), razorpayKeyId);
    }

    @Override
    @Transactional
    public VerifyPaymentResponse verifyPayment(String username, VerifyPaymentRequest request) {
        ensureRazorpayConfig();

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));

        String orderId = request.orderId().trim();
        String paymentId = request.paymentId().trim();
        String signature = request.signature().trim();

        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "payment order not found"));

        if (!payment.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "payment order does not belong to user");
        }

        if ("SUCCESS".equalsIgnoreCase(payment.getStatus())) {
            if (payment.getPaymentId() != null && !payment.getPaymentId().equals(paymentId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payment verification data mismatch");
            }

            Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "wallet not found"));

            return new VerifyPaymentResponse("SUCCESS", "payment already verified", wallet.getBalance());
        }

        paymentRepository.findByPaymentId(paymentId)
            .ifPresent(existingPayment -> {
                if (!existingPayment.getId().equals(payment.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "payment already linked to another order");
                }
            });

        if (!verifySignature(orderId, paymentId, signature)) {
            payment.setPaymentId(paymentId);
            payment.setStatus("FAILED");
            return new VerifyPaymentResponse("FAILED", "invalid payment signature", null);
        }

        Wallet wallet = walletRepository.findByUserId(user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "wallet not found"));

        Wallet lockedWallet = walletRepository.findByIdForUpdate(wallet.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "wallet not found"));

        if (!"ACTIVE".equalsIgnoreCase(lockedWallet.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "wallet is not active");
        }

        lockedWallet.setBalance(lockedWallet.getBalance().add(payment.getAmount()));

        Transaction transaction = new Transaction();
        transaction.setFromWalletId(null);
        transaction.setToWalletId(lockedWallet.getId());
        transaction.setAmount(payment.getAmount());
        transaction.setCurrency(lockedWallet.getCurrency());
        transaction.setTransactionType("CREDIT");
        transaction.setStatus("COMPLETED");
        transaction.setReference(buildTransactionReference(paymentId));
        transaction.setNote("Razorpay top-up");
        transaction.setCompletedAt(OffsetDateTime.now());

        transactionRepository.save(transaction);

        payment.setPaymentId(paymentId);
        payment.setStatus("SUCCESS");

        notificationService.createAndPublish(
            user.getId(),
            NotificationType.CREDIT,
            "Wallet credited",
            "INR " + payment.getAmount() + " added to wallet successfully"
        );

        return new VerifyPaymentResponse("SUCCESS", "payment verified and wallet credited", lockedWallet.getBalance());
    }

    private String createRazorpayOrder(long amountInPaise, UUID userId) {
        try {
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            JSONObject options = new JSONObject();
            options.put("amount", amountInPaise);
            options.put("currency", normalizedCurrency());
            options.put("receipt", buildReceipt(userId));

            Order order = razorpayClient.orders.create(options);
            return order.get("id").toString();
        } catch (RazorpayException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "unable to create payment order", exception);
        }
    }

    private String buildReceipt(UUID userId) {
        String compactUserId = userId.toString().replace("-", "");
        String suffix = compactUserId.substring(0, Math.min(compactUserId.length(), 10));
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        return "RCPT-" + suffix + "-" + randomPart;
    }

    private String buildTransactionReference(String paymentId) {
        return "RZP-" + paymentId;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount is required");
        }
        if (amount.scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount scale cannot exceed 2 decimals");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be greater than zero");
        }
        return amount;
    }

    private long toPaise(BigDecimal amount) {
        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid amount format", exception);
        }
    }

    private boolean verifySignature(String orderId, String paymentId, String signature) {
        String payload = orderId + "|" + paymentId;
        String expectedSignature = hmacSha256(payload, razorpayKeySecret);

        return MessageDigest.isEqual(
            expectedSignature.getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "signature verification error", exception);
        }
    }

    private String normalizedCurrency() {
        return "INR";
    }

    private void ensureRazorpayConfig() {
        if (razorpayKeyId == null || razorpayKeyId.isBlank() || razorpayKeySecret == null || razorpayKeySecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "razorpay configuration is missing");
        }
    }
}
