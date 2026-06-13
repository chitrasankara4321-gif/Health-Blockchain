package com.healthchain.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Encryption utilities - replaces Python's cryptography.fernet.Fernet
 * and the encrypt_data/decrypt_data/hash_data functions from secure_server.py
 */
public class CryptoUtil {

    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int IV_LENGTH = 16;
    private static final int KEY_LENGTH = 256;

    private final SecretKey encryptionKey;
    private final SecretKey signingKey;

    public CryptoUtil() {
        // Generate encryption key (replaces key = Fernet.generate_key())
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(KEY_LENGTH);
            this.encryptionKey = keyGen.generateKey();

            // Generate separate signing key for HMAC
            byte[] signingKeyBytes = new byte[32];
            new SecureRandom().nextBytes(signingKeyBytes);
            this.signingKey = new SecretKeySpec(signingKeyBytes, HMAC_ALGORITHM);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize encryption keys", e);
        }
    }

    /**
     * Simple encrypt for backward compatibility
     * Replaces: encrypt_data_simple(data) / cipher_suite.encrypt(data)
     */
    public byte[] encryptSimple(byte[] data) {
        try {
            // Generate random IV
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // Encrypt
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, ivSpec);
            byte[] encrypted = cipher.doFinal(data);

            // Generate HMAC
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            mac.update(iv);
            byte[] hmac = mac.doFinal(encrypted);

            // Combine: IV + encrypted + HMAC
            byte[] result = new byte[iv.length + encrypted.length + hmac.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            System.arraycopy(hmac, 0, result, iv.length + encrypted.length, hmac.length);

            return result;
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Simple decrypt for backward compatibility
     * Replaces: decrypt_data_simple(encrypted_data) / cipher_suite.decrypt(encrypted_data)
     */
    public byte[] decryptSimple(byte[] encryptedData) {
        try {
            // Extract IV (first 16 bytes)
            byte[] iv = Arrays.copyOfRange(encryptedData, 0, IV_LENGTH);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // Extract HMAC (last 32 bytes)
            byte[] receivedHmac = Arrays.copyOfRange(encryptedData, encryptedData.length - 32, encryptedData.length);

            // Extract encrypted data (between IV and HMAC)
            byte[] encrypted = Arrays.copyOfRange(encryptedData, IV_LENGTH, encryptedData.length - 32);

            // Verify HMAC
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            mac.update(iv);
            byte[] calculatedHmac = mac.doFinal(encrypted);

            if (!MessageDigest.isEqual(receivedHmac, calculatedHmac)) {
                throw new RuntimeException("HMAC verification failed - data may be corrupted");
            }

            // Decrypt
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, ivSpec);
            return cipher.doFinal(encrypted);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Encrypt data with additional security (random padding + integrity hash)
     * Replaces: encrypt_data(data) from secure_server.py
     */
    public Map<String, Object> encryptWithIntegrity(byte[] data) {
        // Add random padding for additional security
        int paddingLength = 16; // AES block size
        byte[] padding = new byte[paddingLength];
        new SecureRandom().nextBytes(padding);

        // padded_data = padding + data + padding
        byte[] paddedData = new byte[padding.length + data.length + padding.length];
        System.arraycopy(padding, 0, paddedData, 0, padding.length);
        System.arraycopy(data, 0, paddedData, padding.length, data.length);
        System.arraycopy(padding, 0, paddedData, padding.length + data.length, padding.length);

        // Encrypt the padded data
        byte[] encryptedData = encryptSimple(paddedData);

        // Add integrity check
        String integrityHash = hashData(encryptedData);

        Map<String, Object> result = new HashMap<>();
        result.put("encrypted_data", encryptedData);
        result.put("integrity_hash", integrityHash);
        result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        return result;
    }

    /**
     * Decrypt data with integrity verification
     * Replaces: decrypt_data(encrypted_data_dict) from secure_server.py
     */
    @SuppressWarnings("unchecked")
    public byte[] decryptWithIntegrity(Map<String, Object> encryptedDataDict) {
        byte[] encryptedData = (byte[]) encryptedDataDict.get("encrypted_data");
        String providedHash = (String) encryptedDataDict.get("integrity_hash");

        // Verify integrity
        String calculatedHash = hashData(encryptedData);
        if (!calculatedHash.equals(providedHash)) {
            throw new RuntimeException("Integrity check failed - data may be corrupted");
        }

        // Decrypt the data
        byte[] decryptedPadded = decryptSimple(encryptedData);

        // Remove padding (first and last 16 bytes)
        if (decryptedPadded.length > 32) {
            return Arrays.copyOfRange(decryptedPadded, 16, decryptedPadded.length - 16);
        } else {
            return decryptedPadded;
        }
    }

    /**
     * Generate SHA-256 hash of data
     * Replaces: hash_data(data) from secure_server.py and server.py
     */
    public String hashData(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    /**
     * Format file size in human readable format
     * Replaces: formatFileSize(bytes) from secure_server.py
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
