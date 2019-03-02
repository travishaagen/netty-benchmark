package com.github.travishaagen.server;

import com.github.travishaagen.client.DigitsClient;
import com.github.travishaagen.client.DigitsClientFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

/**
 * Integration test for client and server.
 */
public class ClientServerTestIT {
    private static final int WAIT_FOR_SERVER_STARTUP_MS = 2000;

    private static final int WAIT_FOR_SERVER_EVENTS_MS = 10_000;

    /**
     * Rule for expecting a call to System.exit(), which helps when testing the 'terminate' command
     */
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    private DigitsServer server;
    private Thread serverThread;

    @Before
    public void startServerInBackgroundThread() {
        // start server in background thread
        server = new DigitsServer();
        // run server until it is shutdown
        final Runnable r = server::run;
        serverThread = new Thread(r);
        serverThread.start();

        try {
            // sleep for a few seconds to allow server to start
            Thread.sleep(WAIT_FOR_SERVER_STARTUP_MS);
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e);
        }
    }

    /**
     * Tests that the server detects the correct number of received and duplicate digits messages.
     */
    @Test
    public void receivedAndDuplicateDigitCountTest() {
        try {
            final DigitsClient client = DigitsClientFactory.connect();

            // send 3 messages, and one duplicate
            client.send(0);
            client.send(1);
            client.send(0);
            client.disconnect();

            // sleep to allow server handle data
            Thread.sleep(WAIT_FOR_SERVER_EVENTS_MS);

            server.statisticsPrinter.runOneIteration();
            Assert.assertTrue("incorrect totalReceivedCount", server.statisticsPrinter.totalReceivedCount == 3);
            Assert.assertTrue("incorrect totalDuplicateCount", server.statisticsPrinter.totalDuplicateCount == 1);
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e);
        } finally {
            server.shutdownGracefully();
        }
    }

    /**
     * Connect with two clients and then issue terminate command.
     */
    @Test
    public void twoClientsAndTerminateTest() {
        try {
            final DigitsClient clientA = DigitsClientFactory.connect();
            final DigitsClient clientB = DigitsClientFactory.connect();

            clientA.send(0);
            clientB.send(1);

            clientA.terminate();

            // sleep to allow server handle data
            Thread.sleep(WAIT_FOR_SERVER_EVENTS_MS);

            exit.expectSystemExit();
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e);
        } finally {
            server.shutdownGracefully();
        }
    }

//    /**
//     * Send large number of digits using one client connection.
//     */
//    @Test()
//    public void singleClientThroughputTest() {
//        try {
//            final DigitsClient client = DigitsClientFactory.connect();
//
//            final long startMs = System.currentTimeMillis();
//
//            final int n = 100000; //DigitsClient.MAX_VALUE + 1;
//            for (int i = DigitsClient.MIN_VALUE; i < n; ++i) {
//                client.send(i);
//            }
//
//            final long endMs = System.currentTimeMillis();
//            System.out.println("Wrote " + n + " values in " + (endMs - startMs) + " milliseconds");
//
//            client.disconnect();
//
//            // sleep to allow server handle data
//            Thread.sleep(WAIT_FOR_SERVER_EVENTS_MS);
//
//            server.statisticsPrinter.runOneIteration();
//            Assert.assertTrue("incorrect totalReceivedCount " + server.statisticsPrinter.totalReceivedCount,
//                    server.statisticsPrinter.totalReceivedCount == n);
//            Assert.assertTrue("incorrect totalDuplicateCount " + server.statisticsPrinter.totalDuplicateCount,
//                    server.statisticsPrinter.totalDuplicateCount == 0);
//        } catch (Exception e) {
//            Assert.fail("Unexpected exception: " + e);
//        } finally {
//            server.shutdownGracefully();
//        }
//    }
}
