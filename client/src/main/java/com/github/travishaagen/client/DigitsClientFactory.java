package com.github.travishaagen.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for {@link DigitsClient} instances.
 */
public class DigitsClientFactory
{
    /**
     * Default server port ({@code 4000})
     */
    private static final int DEFAULT_PORT = 4000;

    /**
     * Default server host ({@code localhost})
     */
    private static final String DEFAULT_HOST = "localhost";

    private static final List<EventLoopGroup> groups = new ArrayList<EventLoopGroup>();

    static {
        // create shutdown hook for this factory's connections
        Runtime.getRuntime().addShutdownHook(new Thread(
                new Runnable()
                {
                    @Override
                    public void run()
                    {
                        for (final EventLoopGroup group : groups) {
                            group.shutdownGracefully();
                        }
                    }
                }, "shutdownHook"
        ));
    }

    /**
     * Hidden constructor
     */
    private DigitsClientFactory()
    {
        // does nothing
    }

    /**
     * Connects a {@link DigitsClient} for {@code localhost:4000}
     *
     * @return new {@link DigitsClient} instance
     * @throws com.github.travishaagen.client.DigitsClientException connect failure
     */
    public static DigitsClient connect() throws DigitsClientException
    {
        return connect(DEFAULT_HOST, DEFAULT_PORT);
    }

    /**
     * Connects a {@link DigitsClient}
     *
     * @param host server host
     * @param port server port
     * @return new {@link DigitsClient} instance
     * @throws com.github.travishaagen.client.DigitsClientException connect failure
     */
    public static DigitsClient connect(final String host, final int port) throws DigitsClientException
    {
        try {
            final EventLoopGroup group = new NioEventLoopGroup(1);
            synchronized (groups) {
                groups.add(group);
            }
            final Bootstrap bootstrap = new Bootstrap();
            bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
            bootstrap.option(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, 8 * 1024);
            bootstrap.option(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, 16 * 1024);
            bootstrap.option(ChannelOption.SO_SNDBUF, 16 * 1024);
            bootstrap.option(ChannelOption.SO_RCVBUF, 16 * 1024);
            bootstrap.option(ChannelOption.WRITE_SPIN_COUNT, 32);
            bootstrap.option(ChannelOption.TCP_NODELAY, true);
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>()
                    {
                        @Override
                        protected void initChannel(final SocketChannel ch) throws Exception
                        {
                            ch.pipeline().addLast(new DigitsClientMessageHandler());
                        }
                    });
            return new DigitsClient(bootstrap.connect(host, port).sync().channel());
        } catch (Exception e) {
            throw new DigitsClientException("Unable to connect", e);
        }
    }
}
