package com.playerPlugin.playerTaskX.engine.exception;

public class GraphExecutionException extends RuntimeException {
    public GraphExecutionException(String message) {
        super(message);
    }
    
    public GraphExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
