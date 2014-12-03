package com.github.travishaagen.server;

import com.github.travishaagen.server.journal.DigitsJournal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import org.easymock.EasyMockRunner;
import org.easymock.Mock;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link DigitsServerMessageHandler}.
 */
@RunWith(EasyMockRunner.class)
public class DigitsServerMessageHandlerTest
{
    private static final char LINE_SEPARATOR = '\n';

    private DigitsJournal journalMock = new DigitsJournal()
    {
        @Override
        public void write(ByteBuf buf)
        {
            buf.skipBytes(DIGITS_BYTE_COUNT);
        }

        @Override
        public void shutdown()
        {
            // does nothing
        }
    };

    @Mock
    private ChannelHandlerContext ctxMock;

    /**
     * Tests responses to all valid and invalid input to the method
     * {@link DigitsServerMessageHandler#handleMessage(io.netty.channel.ChannelHandlerContext, io.netty.buffer.ByteBuf)}
     */
    @Test
    public void handleMessageTests()
    {
        final DigitsServerMessageHandler messageHandler = new DigitsServerMessageHandler(journalMock);

        // read one valid command
        try {
            messageHandler.handleMessage(ctxMock, Unpooled.copiedBuffer("000000000" + LINE_SEPARATOR, CharsetUtil.UTF_8));
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e);
        }

        // read two valid commands
        try {
            messageHandler.handleMessage(ctxMock, Unpooled.copiedBuffer(
                    "000000000" + LINE_SEPARATOR + "000000001" + LINE_SEPARATOR, CharsetUtil.UTF_8));
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e);
        }

        // read one terminate command
        try {
            messageHandler.handleMessage(ctxMock, Unpooled.copiedBuffer("terminate" + LINE_SEPARATOR, CharsetUtil.UTF_8));
        } catch (Exception e) {
            if (!(e instanceof TerminateServerException)) {
                Assert.fail("Expected TerminateServerException, but got: " + e);
            }
        }

        // read one valid and a terminate command
        try {
            messageHandler.handleMessage(ctxMock, Unpooled.copiedBuffer(
                    "000000000" + LINE_SEPARATOR + "terminate" + LINE_SEPARATOR, CharsetUtil.UTF_8));
        } catch (Exception e) {
            if (!(e instanceof TerminateServerException)) {
                Assert.fail("Expected TerminateServerException, but got: " + e);
            }
        }

        // read one invalid command, with a message that is too short
        try {
            messageHandler.handleMessage(ctxMock, Unpooled.copiedBuffer("0" + LINE_SEPARATOR, CharsetUtil.UTF_8));
        } catch (Exception e) {
            if (!(e instanceof InvalidMessageException)) {
                Assert.fail("Expected InvalidMessageException, but got: " + e);
            }
        }

        // read one invalid command, with a message that is too long
        try {
            messageHandler.handleMessage(ctxMock, Unpooled.copiedBuffer("0000000000" + LINE_SEPARATOR, CharsetUtil.UTF_8));
        } catch (Exception e) {
            if (!(e instanceof InvalidMessageException)) {
                Assert.fail("Expected InvalidMessageException, but got: " + e);
            }
        }

        // read one valid and one invalid command
        try {
            messageHandler.handleMessage(ctxMock, Unpooled.copiedBuffer(
                    "000000000" + LINE_SEPARATOR + "9" + LINE_SEPARATOR, CharsetUtil.UTF_8));
        } catch (Exception e) {
            if (!(e instanceof InvalidMessageException)) {
                Assert.fail("Expected InvalidMessageException, but got: " + e);
            }
        }
    }
}
