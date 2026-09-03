package com.samlscope.core;

import java.security.SecureRandom;
import java.util.Locale;

public final class Identifiers {
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Identifiers() {}

    public static String newId(String prefix) {
        if (prefix == null || !prefix.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid ID prefix");
        }
        var value = new char[26];
        for (var i = 0; i < value.length; i++) {
            value[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return prefix.toLowerCase(Locale.ROOT) + "_" + new String(value);
    }
}
