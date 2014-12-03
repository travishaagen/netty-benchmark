package com.github.travishaagen.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Message handler for {@link DigitsClient} that reads queued {@code ByteBuf} messages and transmits them.
 * The {@code ByteBuf} should be backed by a {@code DirectBuffer} for best performance when writing to the socket.
 */
public class DigitsClientMessageHandler extends ChannelInboundHandlerAdapter
{
    private final Queue<ByteBuf> outboundQueue;
    private boolean isCloseRequested;

    /**
     * Constructor
     */
    public DigitsClientMessageHandler()
    {
        outboundQueue = new ConcurrentLinkedQueue<ByteBuf>();
    }

    /**
     * @return shared {@code ByteBuf} queue of outbound messages for this channel
     */
    public Queue<ByteBuf> getOutboundQueue()
    {
        return outboundQueue;
    }

    public void close()
    {
        isCloseRequested = true;
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx)
    {
        writeIfPossible(ctx);
    }

    @Override
    public void channelWritabilityChanged(final ChannelHandlerContext ctx)
    {
        writeIfPossible(ctx);
    }

    private final void writeIfPossible(final ChannelHandlerContext ctx)
    {
        boolean needsFlush = false;
        ByteBuf buf;

        while (ctx.channel().isWritable()) {
            // remove item from queue
            buf = outboundQueue.poll();
            if (buf != null) {
                // send message
                ctx.write(buf, ctx.voidPromise());
                needsFlush = true;
            } else if (needsFlush) {
                // no new message, so flush previous messages
                ctx.flush();
                needsFlush = false;
            }

            if (isCloseRequested && outboundQueue.isEmpty()) {
                if (needsFlush) {
                    ctx.flush();
                }
                ctx.close();
                return;
            }
        }

        if (needsFlush) {
            ctx.flush();
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception
    {
        cause.printStackTrace();

        if (ctx.channel().isActive()) {
            ctx.close();
        }
    }
}
