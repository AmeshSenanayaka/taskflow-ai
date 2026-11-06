package com.taskflow.ai.exception;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import org.springframework.http.HttpStatus;

/**
 * A user-safe translation of an LLM provider or LangChain4j failure.
 * <p>
 * The original exception travels as the cause so it can be logged with full detail, but {@link #getMessage()}
 * never contains provider payloads, API keys or internal hostnames: it is what the UI shows the user.
 */
public class AiServiceException extends RuntimeException {

    private final HttpStatus status;
    private final boolean retryable;

    public AiServiceException(HttpStatus status, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.retryable = retryable;
    }

    public HttpStatus status() {
        return status;
    }

    public boolean retryable() {
        return retryable;
    }

    /** Maps the exceptions LangChain4j and the OpenAI client throw onto HTTP statuses and plain-language messages. */
    public static AiServiceException translate(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root
                && !(root instanceof RateLimitException || root instanceof TimeoutException
                || root instanceof AuthenticationException || root instanceof ModelNotFoundException
                || root instanceof InvalidRequestException)) {
            root = root.getCause();
        }

        if (root instanceof RateLimitException) {
            return new AiServiceException(HttpStatus.TOO_MANY_REQUESTS,
                    "The AI service is busy right now. Please try again in a few seconds.", true, error);
        }
        if (root instanceof TimeoutException || root instanceof java.net.http.HttpTimeoutException
                || root instanceof java.util.concurrent.TimeoutException) {
            return new AiServiceException(HttpStatus.GATEWAY_TIMEOUT,
                    "The AI service took too long to answer. Please try again.", true, error);
        }
        if (root instanceof AuthenticationException) {
            return new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI service rejected the configured API key. Check OPENAI_API_KEY.", false, error);
        }
        if (root instanceof ModelNotFoundException) {
            return new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The configured model is not available. Check langchain4j.open-ai.chat-model.model-name.", false, error);
        }
        if (root instanceof InvalidRequestException) {
            return new AiServiceException(HttpStatus.BAD_GATEWAY,
                    "The AI service rejected the request. Try rephrasing your message.", false, error);
        }
        if (root instanceof java.net.ConnectException || root instanceof java.net.UnknownHostException) {
            return new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Cannot reach the AI service. Check your network connection.", true, error);
        }
        return new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                "The AI assistant hit an unexpected problem. Please try again.", true, error);
    }
}
