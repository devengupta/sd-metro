package com.sdmetro.app;

import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Password hashing and random ids. Pure Java. */
public final class Crypto {
    private Crypto() { }

    public static final int ITERATIONS = 120000;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** n random bytes as lowercase hex. */
    public static String randHex(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return hex(b);
    }

    /** PBKDF2-HMAC-SHA256 (120,000 iterations, 256 bits) of a password with a hex salt; lowercase hex. */
    public static String hash(String password, String saltHex) throws Exception {
        byte[] salt = new byte[saltHex.length() / 2];
        for (int i = 0; i < salt.length; i++) salt[i] = (byte) Integer.parseInt(saltHex.substring(i * 2, i * 2 + 2), 16);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        try {
            return hex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } finally {
            spec.clearPassword();
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
        return sb.toString();
    }
}
