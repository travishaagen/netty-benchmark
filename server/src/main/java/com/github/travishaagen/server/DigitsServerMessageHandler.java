package com.github.travishaagen.server;

import com.github.travishaagen.server.journal.DigitsJournal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufProcessor;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side handler for <em>digits</em> messages.
 */
public class DigitsServerMessageHandler extends ChannelInboundHandlerAdapter
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DigitsServerMessageHandler.class);

    /**
     * ls
     * Exception thrown when a client requests that the server be completely shutdown. This object is pre-allocated
     * for maximum speed.
     */
    private static final TerminateServerException TERMINATE_SERVER_EXCEPTION = new TerminateServerException();

    /**
     * Exception thrown when a client sends an invalid message and should be disconnected. This object is pre-allocated
     * for maximum speed.
     */
    private static final InvalidMessageException INVALID_MESSAGE_EXCEPTION = new InvalidMessageException();

    static {
        TERMINATE_SERVER_EXCEPTION.setStackTrace(new StackTraceElement[0]);
        INVALID_MESSAGE_EXCEPTION.setStackTrace(new StackTraceElement[0]);
    }

    private static final byte ZERO_CHAR_BYTE = '\u0030';
    private static final byte NINE_CHAR_BYTE = '\u0039';
    private static final byte NEWLINE_CHAR_BYTE = '\n';
    private static final byte[] TERMINATE_BYTES = ("terminate\n").getBytes(CharsetUtil.UTF_8);
    private static final int MESSAGE_DIGIT_COUNT = 9;
    public static final int MESSAGE_LINE_LENGTH = MESSAGE_DIGIT_COUNT + 1;

    /**
     * Thread-safe journal for writing unique digits messages to a file
     */
    private final DigitsJournal journal;

    /**
     * Buffer for incomplete message frames, which we reuse while this channel is open
     */
    private ByteBuf singleFrameBuf = Unpooled.buffer(MESSAGE_LINE_LENGTH, MESSAGE_LINE_LENGTH);

    /**
     * Processor for validating digit-messages, which we reuse while this channel is open
     */
    final DigitLineProcessor digitLineProcessor = new DigitLineProcessor();

    /**
     * Constructor
     *
     * @param journal digits journal, for persisting unique digits to disk
     */
    public DigitsServerMessageHandler(final DigitsJournal journal)
    {
        this.journal = journal;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception
    {
        try {
            final ByteBuf buf = (ByteBuf) msg;
            if (buf.readableBytes() != 0) {
                if (singleFrameBuf.readableBytes() != 0) {
                    // we previously had a partial frame, so now we fill in the remaining bytes
                    int writeCount = Math.min(singleFrameBuf.writableBytes(), buf.readableBytes());
                    singleFrameBuf.writeBytes(buf, buf.readerIndex(), writeCount);
                    buf.skipBytes(writeCount);
                    if (singleFrameBuf.readableBytes() == MESSAGE_LINE_LENGTH) {
                        // now the frame is full, so handle that message
                        handleMessage(ctx, singleFrameBuf);
                        if (buf.readableBytes() != 0) {
                            // handle subsequent messages in our buffer
                            handleMessage(ctx, buf);
                        }
                    }
                } else {
                    // handle messages in the buffer
                    handleMessage(ctx, buf);
                }
            }
        } finally {
            // decrement pooled ByteBuf reference count
            ReferenceCountUtil.release(msg);
        }
    }

    /**
     * Handles a client channel message.
     *
     * @param ctx channel context
     * @param buf message bytes
     * @throws TerminateServerException client requested server shutdown
     * @throws InvalidMessageException  client sent invalid message and should be disconnected
     */
    protected void handleMessage(final ChannelHandlerContext ctx, final ByteBuf buf)
            throws TerminateServerException, InvalidMessageException
    {
        // index of last matched character (may not have been a full line match)
        int index;
        while (buf.readableBytes() != 0) {
            if (buf.readableBytes() < MESSAGE_LINE_LENGTH) {
                // could be an incomplete frame, so store the bytes
                singleFrameBuf.clear();
                singleFrameBuf.writeBytes(buf);
                return;
            }

            index = buf.forEachByte(digitLineProcessor);
            if (index != -1 && index - buf.readerIndex() + 1 == MESSAGE_LINE_LENGTH) {
                // valid digits-message, so write to the journal
                journal.write(buf);

                // skip over newline byte
                buf.skipBytes(1);

                // reset digit-line processor for next iteration
                digitLineProcessor.reset();
            } else {
                // invalid digits-message, so check for terminate-message
                index = buf.forEachByte(new TerminateLineProcessor());
                if (index != -1 && index - buf.readerIndex() + 1 == TERMINATE_BYTES.length) {
                    throw TERMINATE_SERVER_EXCEPTION;
                } else {
                    throw INVALID_MESSAGE_EXCEPTION;
                }
            }
        }
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) throws Exception
    {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception
    {
        if (cause instanceof TerminateServerException) {
            // trigger server shutdown-hook
            System.exit(0);
        } else if (cause instanceof InvalidMessageException) {
            // client sent invalid message so disconnect
            ctx.close();
        } else {
            LOGGER.error("Unexpected channel exception", cause);
            ctx.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception
    {
        super.channelInactive(ctx);
        singleFrameBuf.release();
    }

    /**
     * Matches one line of nine digits followed by platform specific newline characters.
     */
    private static class DigitLineProcessor implements ByteBufProcessor
    {
        /**
         * Number of characters processed
         */
        private int charCount;

        @Override
        public boolean process(final byte value) throws Exception
        {
            if (charCount < MESSAGE_DIGIT_COUNT) {
                // still have digits to match
                if (value >= ZERO_CHAR_BYTE && value <= NINE_CHAR_BYTE) {
                    ++charCount;
                    return true;
                }
            } else if (charCount < MESSAGE_LINE_LENGTH) {
                // matched all digits, so if newline character matches, then return false to end matching at this index
                return value != NEWLINE_CHAR_BYTE;
            }
            return false;
        }

        /**
         * Resets state of this class so that it can be reused.
         */
        public void reset()
        {
            charCount = 0;
        }
    }

    /**
     * Matches the string "terminate" followed by platform specific newline characters, which is used to
     * signal that the server should shutdown completely.
     */
    private static class TerminateLineProcessor implements ByteBufProcessor
    {
        /**
         * Index within terminate-string
         */
        private int index;

        @Override
        public boolean process(final byte value) throws Exception
        {
            if (index < TERMINATE_BYTES.length && TERMINATE_BYTES[index] == value) {
                ++index;
                return index < TERMINATE_BYTES.length;
            }
            return false;
        }
    }
}
