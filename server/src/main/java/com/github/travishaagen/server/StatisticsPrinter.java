package com.github.travishaagen.server;

import com.google.common.util.concurrent.AbstractScheduledService;

import java.util.concurrent.TimeUnit;

/**
 * Gathers statistics on received and duplicate digits-messages (see {@link #update(long, long)}, and periodically
 * prints them to standard output for a given time period.
 */
public class StatisticsPrinter extends AbstractScheduledService
{
    /**
     * Sleep duration, in milliseconds, between printing statistics and resetting them for the next iteration.
     */
    private static final long SLEEP_DURATION_MS = 10000;

    /** Total messages received since server startup */
    protected long totalReceivedCount;

    /** Total duplicate messages received since server startup */
    protected long totalDuplicateCount;

    /** Messages received during current period */
    protected long receivedCount;

    /** Duplicates received during current period */
    protected long duplicateCount;

    /**
     * Updates counters. This method can safely be called by multiple threads concurrently.
     *
     * @param received  Number of received messages to add to the received-counter
     * @param duplicate Number of duplicate messages to add to the duplicate-counter
     */
    public void update(final long received, final long duplicate)
    {
        if (received > 0 || duplicate > 0) {
            if (duplicate > received) {
                throw new IllegalArgumentException("`duplicate` argument should never be greater than `received`");
            }
            synchronized (this) {
                if (received != 0) {
                    receivedCount += received;
                }
                if (duplicate != 0) {
                    duplicateCount += duplicate;
                }
            }
        }
    }

    @Override
    protected void runOneIteration() throws Exception
    {
        // read and reset counters
        final long received;
        final long duplicate;
        if (receivedCount == 0) {
            // there should never be any duplicates if there are no received
            received = 0;
            duplicate = 0;
        } else {
            synchronized (this) {
                totalReceivedCount += receivedCount;
                received = receivedCount;
                receivedCount = 0L;

                totalDuplicateCount += duplicateCount;
                duplicate = duplicateCount;
                duplicateCount = 0L;
            }
        }

        // print "received X numbers, Y duplicates" to standard out
        System.out.println(new StringBuilder(64).append("received ").append(received)
                .append(" numbers, ").append(duplicate).append(" duplicates").toString());
    }

    @Override
    protected Scheduler scheduler()
    {
        return Scheduler.newFixedRateSchedule(SLEEP_DURATION_MS, SLEEP_DURATION_MS, TimeUnit.MILLISECONDS);
    }
}
