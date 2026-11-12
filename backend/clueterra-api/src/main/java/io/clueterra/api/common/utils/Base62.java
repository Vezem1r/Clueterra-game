package io.clueterra.api.common.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Base62 {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    public static String encode(long value) {
        if (value == 0) return "0";

        boolean negative = value < 0;
        long v = negative ? -(value + 1) : value;

        StringBuilder sb = new StringBuilder();
        while (v > 0) {
            sb.append(ALPHABET.charAt((int) (v % BASE)));
            v /= BASE;
        }
        if (negative) {
            sb.append('-');
        }
        return sb.reverse().toString();
    }

    public static long decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("Empty Base62 value");
        }

        boolean negative = encoded.charAt(0) == '-';
        int start = negative ? 1 : 0;

        long result = 0;
        for (int i = start; i < encoded.length(); i++) {
            int digit = ALPHABET.indexOf(encoded.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("Invalid Base62 character: " + encoded.charAt(i));
            }
            result = result * BASE + digit;
        }
        return negative ? -(result + 1) : result;
    }
}
