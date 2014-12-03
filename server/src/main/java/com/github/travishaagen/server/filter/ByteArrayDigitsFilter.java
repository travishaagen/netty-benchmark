package com.github.travishaagen.server.filter;

/**
 * {@code byte[]} backed implementation of {@link com.github.travishaagen.server.filter.DigitsFilter}.
 * Only one instance of this class should be created per application (singleton), because of high memory usage, and it
 * is <b>not</b> thread-safe.
 */
public class ByteArrayDigitsFilter implements DigitsFilter
{
    /**
     * Since we will only receive up to 9 digits, there are 1 billion unique possible integers, and we can map
     * those values to 125,000,000 bytes (8 bits each), so it is possible to keep track of which values we have
     * seen before within this byte array. This 119 Mb array is far smaller and faster than a data structure
     * that could keep track of all possible integers.
     */
    private final byte[] lookupBuffer = new byte[125000000];

    /**
     * Converts nine digits into an integer and then determines if it is a unique value.
     *
     * @param bytes nine digits
     * @return {@code true} if value is unique and was added to the lookup-table, and {@code false} otherwise
     */
    public boolean isUnique(final byte[] bytes)
    {
        // convert bytes to a single integer value (See http://stackoverflow.com/a/10578427)
        int value = 0;
        int digit, j, k;
        for (int i = 0; i < bytes.length; ++i) {
            digit = (int) bytes[i] & 0xF;
            if (digit != 0) {
                for (j = 0, k = bytes.length - 1 - i; j < k; ++j) {
                    digit *= 10;
                }
                value += digit;
            }
        }

        // find table position and add to table if unique
        final int bucketIndex = value / 8;
        final int bucketBit = 1 << (value % 8);
        final byte bucketByte = lookupBuffer[bucketIndex];
        if ((bucketByte & bucketBit) == 0) {
            lookupBuffer[bucketIndex] |= (byte) (bucketByte | bucketBit);
            return true;
        }
        return false;
    }
}
