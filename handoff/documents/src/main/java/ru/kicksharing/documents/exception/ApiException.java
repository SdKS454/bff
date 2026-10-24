package ru.kicksharing.documents.exception;

public class ApiException extends RuntimeException {
    private final int statusCode;

    public ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    // Статические фабрики для удобства
    public static ApiException notFound(String msg) {
        return new ApiException(404, msg);
    }

    public static ApiException conflict(String msg) {
        return new ApiException(409, msg);
    }

    public static ApiException forbidden(String msg) {
        return new ApiException(403, msg);
    }

    public static ApiException badRequest(String msg) {
        return new ApiException(400, msg);
    }

    public static ApiException serviceUnavailable(String msg) {
        return new ApiException(503, msg);
    }
}