package com.github.travishaagen.server;

/**
 * {@code Exception} that signals that a client sent an invalid message and should have its connection closed.
 */
public class InvalidMessageException extends Exception {
    public InvalidMessageException() {
        super();
    }
}
