package com.example.security;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class AppLockManager {

    private static final String PREFS_LOCK = "gam3ya_lock_prefs";
    private static final String KEY_PIN_ENABLED = "pin_enabled";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String SALT = "Tahwisha_Local_Salt_v1_";

    private static volatile boolean sessionUnlocked = false;

    private AppLockManager() {
    }

    public static boolean isPinEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_LOCK, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_PIN_ENABLED, false)
                && prefs.getString(KEY_PIN_HASH, null) != null;
    }

    public static boolean setPin(Context context, String pin4Digits) {
        if (!isValidPinFormat(pin4Digits)) {
            return false;
        }
        String hash = hashPin(pin4Digits);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_LOCK, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_PIN_ENABLED, true)
                .putString(KEY_PIN_HASH, hash)
                .apply();
        sessionUnlocked = true;
        return true;
    }

    public static boolean verifyPin(Context context, String pin4Digits) {
        if (!isValidPinFormat(pin4Digits)) {
            return false;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_LOCK, Context.MODE_PRIVATE);
        String storedHash = prefs.getString(KEY_PIN_HASH, null);
        if (storedHash == null) {
            return false;
        }
        String candidateHash = hashPin(pin4Digits);
        boolean match = storedHash.equals(candidateHash);
        if (match) {
            sessionUnlocked = true;
        }
        return match;
    }

    public static boolean disablePin(Context context, String currentPin) {
        if (!verifyPin(context, currentPin)) {
            return false;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_LOCK, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_PIN_ENABLED, false)
                .remove(KEY_PIN_HASH)
                .apply();
        sessionUnlocked = true;
        return true;
    }

    public static boolean isValidPinFormat(String pin) {
        if (pin == null || pin.length() != 4) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (!Character.isDigit(pin.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isSessionUnlocked() {
        return sessionUnlocked;
    }

    public static void unlockSession() {
        sessionUnlocked = true;
    }

    public static void lockSession() {
        sessionUnlocked = false;
    }

    private static String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest((SALT + pin).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : encoded) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            return String.valueOf((SALT + pin).hashCode());
        }
    }
}
