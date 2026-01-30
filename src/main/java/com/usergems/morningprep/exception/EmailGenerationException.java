package com.usergems.morningprep.exception;

/**
 * Exception thrown when email generation fails.
 */
public class EmailGenerationException extends RuntimeException {

    public EmailGenerationException(String message) {
        super(message);
    }

    public EmailGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
