package ru.scooter.user.exception;

/** Storage failure, independent of HTTP transport. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
