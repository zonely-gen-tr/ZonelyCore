package dev.zonely.whiteeffect.auth.twofactor;

import java.util.Arrays;

final class Base32 {

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int[] LOOKUP = new int[128];

    static {
        Arrays.fill(LOOKUP, -1);
        for (int i = 0; i < ALPHABET.length; i++) {
            LOOKUP[ALPHABET[i]] = i;
        }
    }

    private Base32() {
    }

    static String encode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int index = 0;
        int digit;
        int currByte;
        int nextByte;

        for (int i = 0; i < data.length; ) {
            currByte = data[i++] & 0xFF;
            if (index > 3) {
                if (i < data.length) {
                    nextByte = data[i] & 0xFF;
                } else {
                    nextByte = 0;
                }
                digit = currByte & (0xFF >> index);
                index = (index + 5) % 8;
                digit <<= index;
                digit |= nextByte >> (8 - index);
                sb.append(ALPHABET[digit]);
            } else {
                digit = (currByte >> (8 - (index + 5))) & 0x1F;
                index = (index + 5) % 8;
                if (index == 0) {
                    i++;
                }
                sb.append(ALPHABET[digit]);
            }
        }

        return sb.toString();
    }

    static byte[] decode(String value) {
        if (value == null || value.isEmpty()) {
            return new byte[0];
        }
        char[] chars = value.trim().toUpperCase().toCharArray();
        int buffer = 0;
        int bitsLeft = 0;
        byte[] result = new byte[chars.length * 5 / 8];
        int offset = 0;

        for (char c : chars) {
            if (c >= LOOKUP.length || LOOKUP[c] == -1) {
                continue;
            }
            buffer <<= 5;
            buffer |= LOOKUP[c];
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[offset++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        if (offset != result.length) {
            result = Arrays.copyOf(result, offset);
        }
        return result;
    }
}
