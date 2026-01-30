package com.usergems.morningprep.exception;

/**
 * Exception thrown when Calendar API operations fail.
 */
public class CalendarApiException extends RuntimeException {

    public CalendarApiException(String message) {
        super(message);
    }

    public CalendarApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
