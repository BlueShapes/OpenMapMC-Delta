// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.web;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Signed, persistent sessions have no server-side expiry; changing the password revokes them. */
public final class WebMapAuth {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final byte[] passwordDigest;
    private final byte[] signingKey;

    public WebMapAuth(String password, byte[] secret) {
        passwordDigest = digest(password.getBytes(StandardCharsets.UTF_8));
        byte[] material = new byte[secret.length + passwordDigest.length];
        System.arraycopy(secret, 0, material, 0, secret.length);
        System.arraycopy(passwordDigest, 0, material, secret.length, passwordDigest.length);
        signingKey = digest(material);
    }

    public boolean checkPassword(String candidate) {
        return candidate != null && candidate.length() <= 1024
                && MessageDigest.isEqual(passwordDigest, digest(candidate.getBytes(StandardCharsets.UTF_8)));
    }

    public String newSession() {
        byte[] nonce = new byte[32];
        new SecureRandom().nextBytes(nonce);
        String value = ENCODER.encodeToString(nonce);
        return value + "." + ENCODER.encodeToString(sign(value));
    }

    public boolean verify(String token) {
        if (token == null || token.length() != 87 || token.charAt(43) != '.') return false;
        try {
            if (DECODER.decode(token.substring(0, 43)).length != 32) return false;
            byte[] signature = DECODER.decode(token.substring(44));
            return MessageDigest.isEqual(sign(token.substring(0, 43)), signature);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static byte[] digest(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
}
