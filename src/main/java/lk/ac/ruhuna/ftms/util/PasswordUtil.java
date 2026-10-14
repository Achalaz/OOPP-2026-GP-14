
package lk.ac.ruhuna.ftms.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class PasswordUtil {

    private static final String ALGORITHM =
            "PBKDF2WithHmacSHA256";

    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private PasswordUtil() {
    }

    // Use when creating or resetting a user password
    public static String hashPassword(char[] password) {

        if (password == null || password.length == 0) {
            throw new IllegalArgumentException(
                    "Password cannot be empty"
            );
        }

        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);

        byte[] hash = deriveKey(
                password, salt, ITERATIONS
        );

        return ITERATIONS + ":"
                + Base64.getEncoder().encodeToString(salt)
                + ":"
                + Base64.getEncoder().encodeToString(hash);
    }

    // Use during login
    public static boolean verifyPassword(
            char[] enteredPassword,
            String storedHash) {

        if (enteredPassword == null || storedHash == null) {
            return false;
        }

        String[] parts = storedHash.split(":", -1);

        if (parts.length != 3) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[0]);

            if (iterations < 100_000 ||
                    iterations > 1_000_000) {
                return false;
            }

            byte[] salt =
                    Base64.getDecoder().decode(parts[1]);

            byte[] expectedHash =
                    Base64.getDecoder().decode(parts[2]);

            if (salt.length != SALT_BYTES ||
                    expectedHash.length != KEY_BITS / 8) {
                return false;
            }

            byte[] actualHash = deriveKey(
                    enteredPassword, salt, iterations
            );

            try {
                return MessageDigest.isEqual(
                        expectedHash, actualHash
                );
            } finally {
                Arrays.fill(actualHash, (byte) 0);
            }

        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] deriveKey(
            char[] password,
            byte[] salt,
            int iterations) {

        PBEKeySpec spec = new PBEKeySpec(
                password,
                salt,
                iterations,
                KEY_BITS
        );

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance(ALGORITHM);

            return factory.generateSecret(spec)
                    .getEncoded();

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Password hashing failed", e
            );
        } finally {
            spec.clearPassword();
        }
    }
}
