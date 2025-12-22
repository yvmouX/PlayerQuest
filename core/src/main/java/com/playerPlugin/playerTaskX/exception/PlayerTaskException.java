package com.playerPlugin.playerTaskX.exception;

import org.jetbrains.annotations.NotNull;

/**
 * PlayerTaskX Base Exception
 */
public class PlayerTaskException extends RuntimeException {
    public PlayerTaskException(@NotNull String message) {
        this(message, null);
    }

    public PlayerTaskException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getMessage() {
        return super.getMessage();
    }
}
