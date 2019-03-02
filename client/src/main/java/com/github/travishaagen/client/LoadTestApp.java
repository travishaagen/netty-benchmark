package com.github.travishaagen.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Load test app that sends the same digits-message repeatedly, as fast as possible.
 */
public class LoadTestApp {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadTestApp.class);

    private LoadTestApp() {
        // empty
    }

    /**
     * Run five clients, in five threads, through all possible 9 digit values.
     *
     * @param args first argument is the hostname or IP address
     * @throws Exception
     */
    public static void main(final String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Must provide hostname argument");
            return;
        }
        final String host = args[0];
        LOGGER.info("Client connecting to host at {}:4000", host);

        final int availableProcessors = Runtime.getRuntime().availableProcessors();
        final ChannelHandler channelHandler = new LoadTestMessageHandler();

        // group with default number of threads
        final EventLoopGroup group = new NioEventLoopGroup(availableProcessors);
        final Bootstrap bootstrap = new Bootstrap();
        bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        bootstrap.option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 16 * 1024));
        bootstrap.option(ChannelOption.SO_SNDBUF, 16 * 1024);
        bootstrap.option(ChannelOption.SO_RCVBUF, 16 * 1024);
        bootstrap.option(ChannelOption.WRITE_SPIN_COUNT, 32);
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        bootstrap.group(group);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(final SocketChannel ch) throws Exception {
                ch.pipeline().addLast(channelHandler);
            }
        });

        // create shutdown hook for this factory's connections
        Runtime.getRuntime().addShutdownHook(new Thread(group::shutdownGracefully, "shutdownHook"));

        for (int i = availableProcessors - 2; i > -1; --i) {
            bootstrap.connect(host, 4000).sync().channel();
        }
        final Channel channel = bootstrap.connect(host, 4000).sync().channel();

        while (channel.isOpen()) {
            Thread.sleep(5000);
        }
    }
}
