package com.github.travishaagen.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;

/**
 * Client that communicates with a {@code DigitsServer}. This class is not thread safe.
 */
public class DigitsClient
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DigitsClient.class);

    /**
     * Newline sequence expected by the server.
     */
    private static final byte NEWLINE = '\n';

    /**
     * Terminate message, expected by the server for shutdown request.
     */
    private static final byte[] TERMINATE_MESSAGE_BYTES = ("terminate\n").getBytes(CharsetUtil.UTF_8);

    /**
     * Bytes for a UTF-8 zero character array
     */
    private static final byte[] ZERO_CHAR_BYTES = "000000000".getBytes(CharsetUtil.UTF_8);

    /**
     * All digits as UTF-8 character-bytes
     */
    private static final byte[] DIGITS_CHARS = ("0123456789").getBytes(CharsetUtil.UTF_8);

    /**
     * Message bytes used for debugging and network-client throughput tests
     */
    private static final byte[] DEBUG_MSG = ("123456789\n").getBytes(CharsetUtil.UTF_8);

    /**
     * Minimum value accepted by {@link #send(int)}, which is {@code 0}
     */
    public static final int MIN_VALUE = 0;

    /**
     * Maximum value accepted by {@link #send(int)}, which is {@code 999999999}
     */
    public static final int MAX_VALUE = 999999999;

    /**
     * Connection to server
     */
    private final Channel channel;

    /**
     * Queue of digit-messages to be sent to server
     */
    private final Queue<ByteBuf> outboundQueue;

    /**
     * Reusable buffer of nine digit characters
     */
    private final byte[] digits = new byte[9];

    /**
     * Constructor
     *
     * @param channel client's channel to the server
     */
    public DigitsClient(final Channel channel)
    {
        this.channel = channel;
        outboundQueue = ((DigitsClientMessageHandler) channel.pipeline().first()).getOutboundQueue();
    }

    /**
     * Checks for connection to server
     *
     * @return {@code true} if currently connected to server
     */
    public final boolean isConnected()
    {
        return channel.isActive();
    }

    /**
     * Disconnects from the server after sending all pending messages
     */
    public void disconnect() throws DigitsClientException
    {
        if (isConnected()) {
            try {
                ((DigitsClientMessageHandler) channel.pipeline().first()).close();
                channel.closeFuture().sync();
            } catch (Exception e) {
                throw new DigitsClientException("Error while disconnecting", e);
            }
        }
    }

    /**
     * Requests server shutdown
     */
    public void terminate() throws DigitsClientException
    {
        if (!isConnected()) {
            throw new DigitsClientException("not connected");
        }
        outboundQueue.offer(channel.alloc().buffer(10, 10).writeBytes(TERMINATE_MESSAGE_BYTES));
    }

    /**
     * Sends digits to the server. The value must be in the range [0, 999999999].
     *
     * @param value value to send
     */
    public void send(final int value) throws DigitsClientException
    {
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalArgumentException("value is out of valid range [0, 999999999]");
        }

        if (!isConnected()) {
            throw new DigitsClientException("not connected");
        }

        final ByteBuf buf = channel.alloc().buffer(10, 10);
        final int digitsLength = IntegerUtil.toUnicodeChars(value, digits);

        // pad with zeros
        if (digitsLength != 9) {
            buf.writeBytes(ZERO_CHAR_BYTES, 0, 9 - digitsLength);
        }

        // copy digits
        for (int i = 0; i < digitsLength; ++i) {
            // strip off high order bits from char, because we don't need them
            buf.writeByte(digits[i]);
        }

        // newline
        buf.writeByte(NEWLINE);

        outboundQueue.offer(buf);
    }

    /**
     * Run one client through all possible 9 digit values, while another sends random values.
     *
     * @param args first argument is the hostname or IP address
     * @throws Exception
     */
    public static void main(final String[] args) throws Exception
    {
        if (args.length == 0) {
            System.out.println("Must provide hostname argument");
            return;
        }
        final String host = args[0];
        LOGGER.info("Client connecting to host at {}:4000", host);

        final DigitsClient client1 = DigitsClientFactory.connect(host, 4000);
        final DigitsClient client2 = DigitsClientFactory.connect(host, 4000);

        int random;
        final int n = DigitsClient.MAX_VALUE + 1;
        for (int i = DigitsClient.MIN_VALUE; i < n; ++i) {
            // send sequential values for client1
            client1.send(i);

            // send random values for other client
            random = org.apache.commons.lang3.RandomUtils.nextInt(DigitsClient.MIN_VALUE, n);
            client2.send(random);
        }

        client1.disconnect();
        client2.disconnect();
    }
}
