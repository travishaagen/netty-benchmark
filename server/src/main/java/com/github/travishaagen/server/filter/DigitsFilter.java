package com.github.travishaagen.server.filter;

/**
 * Tracks the uniqueness of nine-digit numbers as they are passed into this data structure.
 */
public interface DigitsFilter {
    /**
     * Converts nine digits into an integer and then determines if it is a unique value.
     *
     * @param bytes nine digits
     * @return {@code true} if value is unique and was added to the lookup-table, and {@code false} otherwise
     */
    boolean isUnique(final byte[] bytes);
}
