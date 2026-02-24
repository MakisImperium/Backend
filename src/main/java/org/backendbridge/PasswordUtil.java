package org.backendbridge;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing utility (PBKDF2).
 *
 * <p>Format:</p>
 * pbkdf2$<iterations>$<saltB64>$<hashB64>
 *
 * <p>This makes hashes self-describing and easy to rotate later.</p>
 */
public final class PasswordUtil {

    private PasswordUtil() {}

    private static final SecureRandom RNG = new SecureRandom();

    private static final int DEFAULT_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    public static String hashPbkdf2(String rawPassword) {
        if (rawPassword == null) rawPassword = "";
        byte[] salt = new byte[SALT_BYTES];
        RNG.nextBytes(salt);

        byte[] hash = pbkdf2(rawPassword.toCharArray(), salt, DEFAULT_ITERATIONS, HASH_BYTES);
        return "pbkdf2$" + DEFAULT_ITERATIONS + "$" +
                Base64.getUrlEncoder().withoutPadding().encodeToString(salt) + "$" +
                Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    public static boolean verifyPbkdf2(String rawPassword, String stored) {
        if (stored == null || stored.isBlank()) return false;
        if (rawPassword == null) rawPassword = "";

        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 4) return false;
            if (!"pbkdf2".equals(parts[0])) return false;

            int it = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = Base64.getUrlDecoder().decode(parts[3]);

            byte[] actual = pbkdf2(rawPassword.toCharArray(), salt, it, expected.length);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int outBytes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, outBytes * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 failed", e);
        }
    }
}