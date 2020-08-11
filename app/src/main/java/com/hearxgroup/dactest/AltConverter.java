package com.hearxgroup.dactest;

import java.math.BigInteger;
import java.util.Base64;

/**
 * Created by David Howe
 * hearX Group (Pty) Ltd.
 */
public class AltConverter {
    final protected static char[] encoding = "0123456789ABCDEF".toCharArray();
    public static String convertToString(int[] arr) {
        char[] encodedChars = new char[arr.length * 4 * 2];
        for (int i = 0; i < arr.length; i++) {
            int v = arr[i];
            int idx = i * 4 * 2;
            for (int j = 0; j < 8; j++) {
                encodedChars[idx + j] = encoding[(v >>> ((7-j)*4)) & 0x0F];
            }
        }
        return new String(encodedChars);
    }

    public static String base16to64(String hex){
        return Base64.getEncoder().encodeToString(new BigInteger(hex, 16).toByteArray());
    }

    public static String hexToString(String hex) {
        StringBuilder sb = new StringBuilder();
        char[] hexData = hex.toCharArray();
        for (int count = 0; count < hexData.length - 1; count += 2) {
            int firstDigit = Character.digit(hexData[count], 16);
            int lastDigit = Character.digit(hexData[count + 1], 16);
            int decimal = firstDigit * 16 + lastDigit;
            sb.append((char)decimal);
        }
        return sb.toString();
    }
}
