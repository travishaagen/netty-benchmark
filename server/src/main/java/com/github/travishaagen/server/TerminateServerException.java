package com.github.travishaagen.server;

/**
 * {@code Exception} that signals that a client requested that the server shutdown completely.
 */
public class TerminateServerException extends Exception {
    public TerminateServerException() {
        super();
    }
}
