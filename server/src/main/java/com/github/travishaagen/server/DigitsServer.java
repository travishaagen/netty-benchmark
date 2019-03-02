package com.github.travishaagen.server;

import com.github.travishaagen.server.journal.DigitsJournal;
import com.github.travishaagen.server.journal.RingBufferDigitsJournal;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server that reads lines of nine UTF-8 digits followed by a system-dependent newline sequence. A single line
 * containing the word {@code terminate}, followed by a newline, will cause the server to shutdown immediately.
 * <p/>
 * By default, the server runs on port 4000 and unique digits are written to {@code numbers.log} in the default JVM
 * temporary directory.
 * <p>
 * Optional runtime JVM arguments allow one to override the default port, journal-file directory, journal
 * ring-buffer wait-strategy, and running a single-threaded event-loop (false by default):
 * </p>
 * <ul>
 * <li>-Dserver.port=4000</li>
 * <li>-Djournal.directory=/some/dir</li>
 * <li>-Djournal.waitStrategy=Sleep</li>
 * <li>-Dserver.isSingleThreadedEventLoop=false</li>
 * </ul>
 * Options for the ring-buffer <a href="https://github.com/LMAX-Exchange/disruptor/wiki/Getting-Started#alternative-wait-strategies">
 * wait-strategy</a> are,
 * <ul>
 * <li>Sleep (default)</li>
 * <li>Yield</li>
 * <li>Busy</li>
 * <li>Block</li>
 * </ul>
 */
public class DigitsServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DigitsServer.class);

    /**
     * Server port (default is {@code 4000})
     */
    private static final int PORT;

    /**
     * Number of threads used to concurrently process client connections
     */
    private static final int WORKER_THREAD_COUNT = 5;

    /**
     * Maximum number of nine-digit messages to queue.
     */
    private static final int MAX_DIGITS_QUEUE_SIZE = 1024 * 1024;

    /**
     * Name of the journal file
     */
    private static final String JOURNAL_FILE_NAME = "numbers.log";

    /**
     * Journal file directory, or {@code java.io.tmpdir} property value by default.
     */
    private static final String JOURNAL_FILE_DIRECTORY;

    /**
     * Wait-strategy to use with ring buffer that writes unique digit-messages to a journal file, or {@code null}
     * to use default (Sleep).
     */
    private static final String RING_BUFFER_WAIT_STRATEGY;

    /**
     * When {@code true}, Netty event-loop will be single-threaded ({@code false} by default)
     */
    private static final boolean SINGLE_THREADED_EVENT_LOOP;

    static {
        // load runtime properties set as JVM arguments, or fallback to defaults
        PORT = NumberUtils.toInt(System.getProperty("server.port"), 4000);

        JOURNAL_FILE_DIRECTORY = StringUtils.defaultString(StringUtils.trimToNull(System.getProperty("journal.directory")),
                System.getProperty("java.io.tmpdir"));

        RING_BUFFER_WAIT_STRATEGY = StringUtils.trimToNull(System.getProperty("journal.waitStrategy"));

        SINGLE_THREADED_EVENT_LOOP = BooleanUtils.toBoolean(System.getProperty("server.isSingleThreadedEventLoop"));
    }

    private AtomicBoolean shutdownFlag = new AtomicBoolean();
    private EventLoopGroup acceptorGroup;
    private EventLoopGroup workerGroup;
    private DigitsJournal journal;
    protected StatisticsPrinter statisticsPrinter;

    /**
     * Runs the server event-loop.
     */
    public void run() {
        LOGGER.info("Starting server on port {} with {} threaded event-loop", PORT,
                SINGLE_THREADED_EVENT_LOOP ? "single" : "multi");

        if (SINGLE_THREADED_EVENT_LOOP) {
            // single thread will accept and select socket data
            acceptorGroup = workerGroup = new NioEventLoopGroup(1);
        } else {
            // (default) use one thread to accept and multiple threads to select socket data concurrently
            acceptorGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup(WORKER_THREAD_COUNT);
        }
        statisticsPrinter = new StatisticsPrinter();

        // can trigger shutdown-hook by killing server process from command-line
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownGracefully, "shutdownHook"));

        try {
            // start printing statistics
            statisticsPrinter.startAsync();

            // delete journal file if it already exists
            final Path journalPath = Paths.get(JOURNAL_FILE_DIRECTORY, JOURNAL_FILE_NAME);
            Files.deleteIfExists(journalPath);
            LOGGER.info("Created journal file at {}", journalPath.toString());

            //journal = new ArrayBlockingQueueDigitsJournal(MAX_DIGITS_QUEUE_SIZE, statisticsPrinter, journalPath.toFile());
            journal = new RingBufferDigitsJournal(MAX_DIGITS_QUEUE_SIZE, RING_BUFFER_WAIT_STRATEGY,
                    SINGLE_THREADED_EVENT_LOOP, statisticsPrinter, journalPath.toFile());

            // initialize server and bind to port
            final ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(acceptorGroup, workerGroup);
            bootstrap.channel(NioServerSocketChannel.class);
            bootstrap.childOption(ChannelOption.SO_SNDBUF, 16 * 1024);
            bootstrap.childOption(ChannelOption.SO_RCVBUF, 16 * 1024);
            bootstrap.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
            bootstrap.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 16 * 1024));
            bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(final SocketChannel ch) throws Exception {
                    // processes messages
                    ch.pipeline().addLast(new DigitsServerMessageHandler(journal));
                }
            });
            bootstrap.bind(PORT).sync().channel().closeFuture().sync();
        } catch (Exception e) {
            LOGGER.error("Unable to start server", e);
            shutdownGracefully();
        }
    }

    /**
     * Starts {@code DigitsServer} from command-line.
     *
     * @param args
     * @throws Exception
     */
    public static void main(final String[] args) throws Exception {
        // start server event-loop
        new DigitsServer().run();
    }

    /**
     * Shuts the server down gracefully, by closing sockets and releasing resources.
     */
    public void shutdownGracefully() {
        if (shutdownFlag.compareAndSet(false, true)) {
            acceptorGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            if (journal != null) {
                journal.shutdown();
            }
            statisticsPrinter.stopAsync();
        }
    }
}
