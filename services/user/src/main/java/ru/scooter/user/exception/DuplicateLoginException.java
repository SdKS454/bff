package ru.scooter.user.exception;

public final class DuplicateLoginException extends DataAccessException {
    public DuplicateLoginException(Throwable cause) {
        super("Login already exists", cause);
    }
}
