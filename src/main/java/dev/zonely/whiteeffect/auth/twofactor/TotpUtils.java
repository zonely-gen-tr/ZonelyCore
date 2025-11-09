package dev.zonely.whiteeffect.auth.twofactor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Locale;

final class TotpUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpUtils() {
    }

    static String generateSecret(int byteLength) {
        byte[] buffer = new byte[Math.max(10, byteLength)];
        RANDOM.nextBytes(buffer);
        return Base32.encode(buffer);
    }

    static boolean verifyCode(String secret,
                              String code,
                              int digits,
                              int allowedDrift,
                              int periodSeconds) {
        if (secret == null || secret.isEmpty() || code == null || code.isEmpty()) {
            return false;
        }
        String normalizedCode = code.replaceAll("\\s+", "");
        if (!normalizedCode.matches("\\d{" + digits + "}")) {
            return false;
        }
        byte[] key = Base32.decode(secret);
        if (key.length == 0) {
            return false;
        }

        long currentWindow = System.currentTimeMillis() / (periodSeconds * 1000L);
        for (int i = -allowedDrift; i <= allowedDrift; i++) {
            String expected = generateTotp(key, currentWindow + i, digits);
            if (expected.equals(normalizedCode)) {
                return true;
            }
        }
        return false;
    }

    static String generateAuthenticatorUri(String issuer,
                                           String accountName,
                                           String secret,
                                           int digits,
                                           int periodSeconds) {
        String formattedIssuer = issuer == null ? "Zonely" : issuer.trim();
        String formattedAccount = accountName == null ? "player" : accountName.trim();
        return String.format(Locale.ROOT,
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=%d&period=%d",
                urlEncode(formattedIssuer),
                urlEncode(formattedAccount),
                secret,
                urlEncode(formattedIssuer),
                digits,
                periodSeconds);
    }

    private static String urlEncode(String input) {
        return URLEncoder.encode(input, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String generateTotp(byte[] key, long timeStep, int digits) {
        try {
            byte[] data = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(timeStep).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, digits);
            return String.format(Locale.ROOT, "%0" + digits + "d", otp);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to compute TOTP value", ex);
        }
    }
}
