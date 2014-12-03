package com.github.travishaagen.client;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;

/**
 * Message handler for {@link com.github.travishaagen.client.DigitsClient} that creates and sends
 * <i>the same message</i> as fast as possible (per channel) until client is shutdown.
 */
@ChannelHandler.Sharable
public class LoadTestMessageHandler extends ChannelInboundHandlerAdapter
{
    private static final byte[] DEBUG_MSG = ("123456789\n").getBytes(CharsetUtil.UTF_8);

    @Override
    public void channelActive(ChannelHandlerContext ctx)
    {
        writeIfPossible(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx)
    {
        writeIfPossible(ctx);
    }

    private final void writeIfPossible(final ChannelHandlerContext ctx)
    {
        while (ctx.channel().isWritable()) {
            ctx.write(ctx.alloc().buffer(10, 10).writeBytes(DEBUG_MSG), ctx.voidPromise());
        }
        ctx.flush();
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
