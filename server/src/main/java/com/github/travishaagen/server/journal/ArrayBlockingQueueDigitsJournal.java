package com.github.travishaagen.server.journal;

import com.google.common.util.concurrent.AbstractScheduledService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Class that writes unique (non-duplicate) nine-digit numbers to a journal file, that is backed by an
 * {@code ArrayBlockingQueue}, for multiple writer threads and a single consumer thread. This class does
 * <b>not</b> pool {@code byte[]} instances that are added to the queue, and {@code byte[]} references are
 * drained into a buffer once every millisecond.
 */
public class ArrayBlockingQueueDigitsJournal implements DigitsJournal
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ArrayBlockingQueueDigitsJournal.class);
    private static final int CONSUMER_SLEEP_DURATION_MS = 1;
    private static final int MAX_BATCH_SIZE = 1024 * 8;

    private final BlockingQueue<byte[]> queue;
    private final QueueConsumer consumer;

    /**
     * Constructor
     *
     * @param maxQueueSize      maximum size of queue for nine-digit strings
     * @param statisticsPrinter statistics printer
     * @param file              journal file
     */
    public ArrayBlockingQueueDigitsJournal(final int maxQueueSize, final StatisticsPrinter statisticsPrinter, final File file)
    {
        queue = new ArrayBlockingQueue<byte[]>(maxQueueSize);
        consumer = new QueueConsumer(MAX_BATCH_SIZE, queue, statisticsPrinter, file);
        consumer.startAsync();
    }

    @Override
    public void write(final ByteBuf buf)
    {
        final byte[] bytes = new byte[DIGITS_BYTE_COUNT];
        buf.readBytes(bytes, 0, DIGITS_BYTE_COUNT);
        try {
            // blocks until room is available, which applies back-pressure
            queue.put(bytes);
        } catch (Exception e) {
            LOGGER.error("Unexpected exception", e);
        }
    }

    @Override
    public void shutdown()
    {
        consumer.stopAsync();
    }

    /**
     * Single-threaded queue consumer that reads from message queue in batches, and writes unique digits-messages to
     * a journal-file.
     */
    private static class QueueConsumer extends AbstractScheduledService
    {
        private static final byte NEWLINE_CHAR_BYTE = '\n';

        private final DigitsFilter digitsFilter;
        private final StatisticsPrinter statisticsPrinter;
        private final BufferedOutputStream fileOutputStream;
        private final BlockingQueue<byte[]> queue;
        private final List<byte[]> batch;
        private final int maxBatchSize;

        /**
         * Constructor
         *
         * @param maxBatchSize      maximum number of items to read from queue in one iteration
         * @param queue             digits queue
         * @param statisticsPrinter statistics printer
         * @param file              journal file
         */
        private QueueConsumer(final int maxBatchSize, final BlockingQueue<byte[]> queue,
                              final StatisticsPrinter statisticsPrinter, final File file)
        {
            this.queue = queue;
            this.statisticsPrinter = statisticsPrinter;
            this.maxBatchSize = maxBatchSize;
            batch = new ArrayList<byte[]>(maxBatchSize);
            try {
                fileOutputStream = new BufferedOutputStream(new FileOutputStream(file), 8192);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("File not found at path: " + file.getAbsolutePath(), e);
            }
            digitsFilter = new ByteBufferDigitsFilter();
        }

        @Override
        protected void runOneIteration() throws Exception
        {
            // copy batch from queue
            queue.drainTo(batch, maxBatchSize);
            if (!batch.isEmpty()) {
                try {
                    final int n = batch.size();
                    int duplicates = 0;
                    byte[] bytes;
                    for (int i = 0; i < n; ++i) {
                        bytes = batch.get(i);
                        if (!digitsFilter.isUnique(bytes)) {
                            // found duplicate, so update stat
                            ++duplicates;
                        } else {
                            // unique value, so write digits followed by newline character
                            fileOutputStream.write(bytes);
                            fileOutputStream.write(NEWLINE_CHAR_BYTE);
                        }
                    }

                    // update stats
                    statisticsPrinter.update(n, duplicates);
                } catch (Exception e) {
                    LOGGER.error("Unexpected exception while writing to journal file", e);
                } finally {
                    // discard messages if an error occurred
                    batch.clear();
                }
            }
        }

        @Override
        protected Scheduler scheduler()
        {
            return Scheduler.newFixedRateSchedule(0, CONSUMER_SLEEP_DURATION_MS, TimeUnit.MILLISECONDS);
        }

        @Override
        protected void shutDown() throws Exception
        {
            // closing the file will also flush its buffer to disk
            fileOutputStream.close();
        }
    }
}
