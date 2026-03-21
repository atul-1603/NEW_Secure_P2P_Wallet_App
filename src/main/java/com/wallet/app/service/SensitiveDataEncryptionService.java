package com.wallet.app.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SensitiveDataEncryptionService {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SensitiveDataEncryptionService(
        @Value("${app.encryption.bank-account-key:local-dev-bank-key-change-before-prod}") String keyMaterial
    ) {
        this.key = deriveKey(keyMaterial);
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value to encrypt is required");
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to encrypt sensitive data", exception);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value to decrypt is required");
        }

        try {
            byte[] payload = Base64.getDecoder().decode(encryptedText);
            if (payload.length <= GCM_IV_LENGTH_BYTES) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "invalid encrypted payload");
            }

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] cipherBytes = new byte[payload.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(payload, 0, iv, 0, GCM_IV_LENGTH_BYTES);
            System.arraycopy(payload, GCM_IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] decrypted = cipher.doFinal(cipherBytes);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to decrypt sensitive data", exception);
        }
    }

    private SecretKey deriveKey(String keyMaterial) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (GeneralSecurityException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to initialize encryption key", exception);
        }
    }
}
