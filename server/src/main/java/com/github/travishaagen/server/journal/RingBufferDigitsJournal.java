package com.github.travishaagen.server.journal;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.github.travishaagen.server.StatisticsPrinter;
import com.github.travishaagen.server.filter.ByteBufferDigitsFilter;
import com.github.travishaagen.server.filter.DigitsFilter;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.concurrent.Executors;

/**
 * Class that writes unique (non-duplicate) nine-digit numbers to a journal file, that is backed by an
 * LMAX Disruptor ring-buffer, for multiple writer threads and a single consumer thread. The ring-buffer pre-allocates
 * {@code byte[]} instances that are used for the duration.
 */
public class RingBufferDigitsJournal implements DigitsJournal
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RingBufferDigitsJournal.class);

    private final Disruptor<byte[]> disruptor;
    private final RingBuffer<byte[]> ringBuffer;
    private final RingBufferConsumer consumer;

    /**
     * Constructor
     *
     * @param ringBufferSize    maximum size of ring-buffer for nine-digit strings
     * @param waitStrategy      ring-buffer wait strategy from the set {Sleep, Yield, Busy, Block} or {@code null} to
     *                          use default (Sleep)
     * @param singleWriter      set to {@code true} if only a single thread will ever write to this {@code DigitsJournal}
     * @param statisticsPrinter statistics printer
     * @param file              journal file
     */
    public RingBufferDigitsJournal(final int ringBufferSize, final String waitStrategy, final boolean singleWriter,
                                   final StatisticsPrinter statisticsPrinter, final File file)
    {
        disruptor = new Disruptor<byte[]>(
                new EventFactory<byte[]>()
                {
                    @Override
                    public byte[] newInstance()
                    {
                        // create a byte array that will occupy a permanent space in the ring buffer
                        return new byte[DIGITS_BYTE_COUNT];
                    }
                }, ringBufferSize, Executors.newCachedThreadPool(),
                singleWriter ? ProducerType.SINGLE : ProducerType.MULTI,
                createDisruptorWaitStrategy(waitStrategy)
        );

        // log disruptor exceptions
        disruptor.handleExceptionsWith(new ExceptionHandler()
        {
            @Override
            public void handleOnStartException(final Throwable t)
            {
                LOGGER.error("Disruptor exception during startup.", t);
            }

            @Override
            public void handleOnShutdownException(final Throwable t)
            {
                LOGGER.error("Disruptor exception during shutdown.", t);
            }

            @Override
            public void handleEventException(final Throwable t, final long sequence, final Object event)
            {
                LOGGER.error(event.toString(), t);
            }
        });

        consumer = new RingBufferConsumer(statisticsPrinter, file);
        disruptor.handleEventsWith(consumer);
        ringBuffer = disruptor.start();
    }

    @Override
    public void write(final ByteBuf buf)
    {
        // copy bytes into ring-buffer slot and publish it
        final long sequence = ringBuffer.next();
        try {
            final byte[] bytes = ringBuffer.get(sequence);
            buf.readBytes(bytes, 0, DIGITS_BYTE_COUNT);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    @Override
    public void shutdown()
    {
        disruptor.shutdown();
        try {
            consumer.shutDown();
        } catch (Exception e) {
            LOGGER.error("Shutdown exception", e);
        }
    }

    /**
     * Creates a new {@code WaitStrategy} instance, based on the given supported values,
     * <ul>
     * <li>Sleep (default)</li>
     * <li>Yield</li>
     * <li>Busy</li>
     * <li>Block</li>
     * </ul>
     *
     * @return new {@code WaitStrategy} instance
     */
    private WaitStrategy createDisruptorWaitStrategy(final String waitStrategy)
    {
        if ("Sleep".equalsIgnoreCase(waitStrategy)) {
            return new SleepingWaitStrategy();
        } else if ("Yield".equalsIgnoreCase(waitStrategy)) {
            return new YieldingWaitStrategy();
        } else if ("Busy".equalsIgnoreCase(waitStrategy)) {
            return new BusySpinWaitStrategy();
        } else if ("Block".equalsIgnoreCase(waitStrategy)) {
            return new BlockingWaitStrategy();
        }
        // default (uses least amount of CPU resources)
        return new BlockingWaitStrategy();
    }

    /**
     * Single-threaded consumer of digits-messages from the ring-buffer, which writes unique values to a journal-file.
     */
    private static class RingBufferConsumer implements EventHandler<byte[]>
    {
        private static final byte NEWLINE_CHAR_BYTE = '\n';

        private final DigitsFilter digitsFilter;
        private final StatisticsPrinter statisticsPrinter;
        private final BufferedOutputStream fileOutputStream;

        private int received;
        private int duplicates;

        /**
         * Constructor
         *
         * @param statisticsPrinter statistics printer
         * @param file              journal file
         */
        private RingBufferConsumer(final StatisticsPrinter statisticsPrinter, final File file)
        {
            this.statisticsPrinter = statisticsPrinter;
            try {
                fileOutputStream = new BufferedOutputStream(new FileOutputStream(file), 8192);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("File not found at path: " + file.getAbsolutePath(), e);
            }
            digitsFilter = new ByteBufferDigitsFilter();
        }

        @Override
        public void onEvent(final byte[] bytes, final long sequence, final boolean endOfBatch) throws Exception
        {
            if (!digitsFilter.isUnique(bytes)) {
                // found duplicate, so update stat
                ++duplicates;
            } else {
                // unique value, so write digits followed by newline character
                fileOutputStream.write(bytes);
                fileOutputStream.write(NEWLINE_CHAR_BYTE);
            }
            ++received;

            if (endOfBatch) {
                // update stats
                statisticsPrinter.update(received, duplicates);
                received = 0;
                duplicates = 0;
            }
        }

        public void shutDown() throws Exception
        {
            // closing the file will also flush its buffer to disk
            fileOutputStream.close();
        }
    }
}
