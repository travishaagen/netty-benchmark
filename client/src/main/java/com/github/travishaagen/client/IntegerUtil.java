package com.github.travishaagen.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * {@code java.lang.Integer} utilities
 */
public final class IntegerUtil
{
    /**
     * The hidden {@code java.lang.Integer.getChars(int, int, char[])} method, which is very fast at converting
     * an Integer to digit characters, but creates too much garbage on its own.
     */
    private static final Method GET_CHARS_METHOD;

    static {
        try {
            // use reflection to access a hidden Integer method called "getChars"
            GET_CHARS_METHOD = Integer.class.getDeclaredMethod("getChars", int.class, int.class, char[].class);
            GET_CHARS_METHOD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("Unable to reflect on Integer methods", e);
        }
    }

    /**
     * Counts the number of digits in an integer.
     *
     * @param i integer value
     * @return number of digits in {@code i}
     */
    private static int digitCount(final int i)
    {
        if (i < 10) return 1;
        if (i < 100) return 2;
        if (i < 1000) return 3;
        if (i < 10000) return 4;
        if (i < 100000) return 5;
        if (i < 1000000) return 6;
        if (i < 10000000) return 7;
        if (i < 100000000) return 8;
        if (i < 1000000000) return 9;
        return 10;
    }

    /**
     * Fills the given {@code char[]} with Unicode characters representing the specified integer's digits.
     * This method is similar to {@link java.lang.Integer#toString(int)}, but it produces less garbage, because it
     * reuses a {@code char[]} and does not construct a {@code String}.
     *
     * @param i   an integer
     * @param buf buffer to fill with Unicode characters
     * @return length of buffer that was filled
     * @throws java.lang.IllegalArgumentException if {@code buf} was too small to hold characters
     */
    public static int toUnicodeChars(final int i, final char[] buf)
    {
        final int size = (i < 0) ? digitCount(-i) + 1 : digitCount(i);
        if (size > buf.length) {
            throw new IllegalArgumentException("buf too small, and should be increased to at least " + size);
        }
        try {
            GET_CHARS_METHOD.invoke(null, i, size, buf);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("unexpected exception", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("unexpected exception", e);
        }
        return size;
    }

    /**
     * Hidden constructor
     */
    private IntegerUtil()
    {
        // empty
    }
}
