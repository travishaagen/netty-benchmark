package com.github.travishaagen.client;

/**
 * Exceptions for {@link DigitsClient} that are related to communicating with the server.
 */
public class DigitsClientException extends Exception
{
    /**
     * Constructor
     *
     * @param message exception message
     */
    public DigitsClientException(String message)
    {
        super(message);
    }

    /**
     * Constructor
     *
     * @param message exception message
     * @param cause   wrapped throwable
     */
    public DigitsClientException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
