package com.github.travishaagen.server.journal;

import io.netty.buffer.ByteBuf;

/**
 * Interface for a digits-message journal implementation, that queues messages from multiple threads and writes
 * unique values to a journal-file.
 */
public interface DigitsJournal
{
    /**
     * Number of bytes for a digits message.
     */
    static final int DIGITS_BYTE_COUNT = 9;

    /**
     * Writes nine UTF-8 digits to the journal file, if the number is unique.
     *
     * @param buf bytes for nine UTF-8 digits, which may be padded with zeros
     */
    void write(final ByteBuf buf);

    /**
     * Close files and release resources on shutdown.
     */
    void shutdown();
}
