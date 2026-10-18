package bff.service;

public class ServiceException extends RuntimeException {

    private final int status;

    public ServiceException(int status, String message) {
        super(message);
        this.status = status;
    }

    public ServiceException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
