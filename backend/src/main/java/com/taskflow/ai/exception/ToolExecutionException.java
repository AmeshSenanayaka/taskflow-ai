package com.taskflow.ai.exception;

/**
 * Thrown by a {@code @Tool} method when it cannot do what the model asked.
 * <p>
 * LangChain4j catches it and returns {@link #getMessage()} to the model as the tool result, so the message is
 * written <em>for the model</em>: it says what went wrong and what to do instead ("use findTasks first",
 * "use yyyy-MM-dd"), and never contains stack traces, SQL or internal details.
 */
public class ToolExecutionException extends RuntimeException {

    public ToolExecutionException(String message) {
        super(message);
    }

    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
