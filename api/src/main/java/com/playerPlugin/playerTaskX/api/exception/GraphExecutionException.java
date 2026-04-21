package com.playerPlugin.playerTaskX.api.exception;

public class GraphExecutionException extends RuntimeException {
    public GraphExecutionException(String message) {
        super(message);
    }
    
    public GraphExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
