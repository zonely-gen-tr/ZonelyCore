package dev.zonely.whiteeffect.auth;

import dev.zonely.whiteeffect.auth.crypto.BCrypt;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;

final class PasswordHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(String algorithm, String rawPassword) {
        String normalized = normalizeAlgorithm(algorithm);
        if ("BCRYPT".equals(normalized) || "BYCRYPT".equals(normalized)) {
            return hashBcrypt(rawPassword);
        }
        return hashSha256(rawPassword);
    }

    public static boolean matches(String algorithm, String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }

        String trimmed = storedHash.trim();
        if (trimmed.regionMatches(true, 0, "bcrypt$", 0, 7) || trimmed.startsWith("$2a$") || trimmed.startsWith("$2b$") || trimmed.startsWith("$2y$")) {
            return checkBcrypt(rawPassword, trimmed);
        }
        if (trimmed.regionMatches(true, 0, "sha256$", 0, 7)
                || trimmed.regionMatches(true, 0, "$SHA$", 0, 5)) {
            return checkSha256(rawPassword, trimmed);
        }

        String normalized = normalizeAlgorithm(algorithm);
        if ("BCRYPT".equals(normalized) || "BYCRYPT".equals(normalized)) {
            return checkBcrypt(rawPassword, trimmed);
        }
        return checkSha256(rawPassword, trimmed);
    }

    private static String hashBcrypt(String rawPassword) {
        String payload = BCrypt.hashpw(rawPassword, BCrypt.gensalt(12));
        return "bcrypt$" + payload;
    }

    private static boolean checkBcrypt(String rawPassword, String storedHash) {
        String payload = storedHash.regionMatches(true, 0, "bcrypt$", 0, 7)
                ? storedHash.substring(7)
                : storedHash;
        try {
            return BCrypt.checkpw(rawPassword, payload);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String hashSha256(String rawPassword) {
        String salt = generateSalt(16);
        String inner = sha256Hex(rawPassword);
        String finalHash = sha256Hex(inner + salt);
        return "$SHA$" + salt + "$" + finalHash;
    }

    private static boolean checkSha256(String rawPassword, String storedHash) {
        String trimmed = storedHash.trim();
        String body = trimmed.regionMatches(true, 0, "sha256$", 0, 7)
                ? storedHash.substring(7)
                : trimmed.regionMatches(true, 0, "$SHA$", 0, 5)
                ? storedHash.substring(5)
                : storedHash.regionMatches(true, 0, "SHA$", 0, 4)
                ? storedHash.substring(4)
                : storedHash;
        String[] split = body.split("\\$");
        if (split.length == 2) {
            String salt = split[0];
            String hash = split[1];
            return verifySha256WithSalt(rawPassword, salt, hash);
        }
        if (split.length == 3) {
            String salt = split[1];
            String hash = split[2];
            return verifySha256WithSalt(rawPassword, salt, hash);
        }
        return body.equalsIgnoreCase(sha256Hex(rawPassword));
    }

    private static boolean verifySha256WithSalt(String rawPassword, String salt, String expectedHash) {
        String inner = sha256Hex(rawPassword);
        String candidate1 = sha256Hex(inner + salt);
        if (expectedHash.equalsIgnoreCase(candidate1)) {
            return true;
        }
        String candidate2 = sha256Hex(salt + inner);
        if (expectedHash.equalsIgnoreCase(candidate2)) {
            return true;
        }
        String candidate3 = sha256Hex(salt + rawPassword);
        if (expectedHash.equalsIgnoreCase(candidate3)) {
            return true;
        }
        return expectedHash.equalsIgnoreCase(sha256Hex(rawPassword + salt));
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static String generateSalt(int length) {
        byte[] buffer = new byte[length];
        RANDOM.nextBytes(buffer);
        return toHex(buffer);
    }

    private static String toHex(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte b : data) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }

    private static String normalizeAlgorithm(String algorithm) {
        return algorithm == null ? "SHA256" : algorithm.trim().toUpperCase(Locale.ROOT);
    }
}
