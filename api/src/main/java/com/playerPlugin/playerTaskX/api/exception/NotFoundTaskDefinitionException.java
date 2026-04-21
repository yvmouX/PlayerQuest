package com.playerPlugin.playerTaskX.api.exception;

import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import org.jetbrains.annotations.NotNull;

public class NotFoundTaskDefinitionException extends PlayerTaskException {
    // TaskDefinition instance
    private TaskDefinition taskDef;

    public NotFoundTaskDefinitionException() {
        super("Player task definition not found");
    }

    public NotFoundTaskDefinitionException(@NotNull String message) {
        super(message);
    }

    public NotFoundTaskDefinitionException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }

    public NotFoundTaskDefinitionException(@NotNull TaskDefinition taskDef, @NotNull String message) {
        super(message);
        this.taskDef = taskDef;
    }

    public NotFoundTaskDefinitionException(@NotNull TaskDefinition taskDef, @NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
        this.taskDef = taskDef;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();

        if (taskDef != null) {
            sb.append("[").append(taskDef.getId()).append("] ");
        }
        sb.append(super.getMessage());

        return sb.toString();
    }
}
