package com.example.dynamicgraphreportui.exceptions;

public class GraphQlApplicationException extends RuntimeException {

    private final String errorCode;

    public GraphQlApplicationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public GraphQlApplicationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
