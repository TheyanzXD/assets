package com.thelostecho.game.managers;

import android.content.Context;
import android.provider.Settings;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Lightweight AES encryption for save files. The key is derived from the
 * device's Android ID plus a hardcoded salt, so save data cannot be trivially
 * tampered with on another device. This is anti-cheat obfuscation, not a
 * security boundary.
 */
public final class EncryptionUtil {

    private static final String SALT = "TheLostEcho#2026$Raka@Aethelgard";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final String ALGORITHM = "AES";

    private EncryptionUtil() {
    }

    /** Derives a 128-bit AES key from the device id + salt. */
    public static SecretKeySpec deriveKey(Context context) {
        try {
            String deviceId = "unknown";
            try {
                deviceId = Settings.Secure.getString(context.getContentResolver(),
                        Settings.Secure.ANDROID_ID);
            } catch (Exception ignored) {
                // Fall back to "unknown"; encryption still works.
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((SALT + deviceId).getBytes(StandardCharsets.UTF_8));
            byte[] keyBytes = new byte[16];
            System.arraycopy(hash, 0, keyBytes, 0, 16);
            return new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (Exception e) {
            return null;
        }
    }

    public static String encrypt(Context context, String plain) {
        try {
            SecretKeySpec key = deriveKey(context);
            if (key == null) {
                return plain;
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(enc, Base64.NO_WRAP);
        } catch (Exception e) {
            return plain;
        }
    }

    public static String decrypt(Context context, String cipherText) {
        try {
            SecretKeySpec key = deriveKey(context);
            if (key == null) {
                return cipherText;
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] dec = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP));
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** Generates a random salt string (used for future save-scrambling). */
    public static String randomSalt() {
        byte[] buf = new byte[16];
        new SecureRandom().nextBytes(buf);
        return Base64.encodeToString(buf, Base64.NO_WRAP);
    }
}
