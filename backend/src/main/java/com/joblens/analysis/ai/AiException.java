package com.joblens.analysis.ai;

import com.joblens.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** Failures of the AI integration. Messages are written for end users. */
public class AiException extends ApiException {

    public AiException(HttpStatus status, String message) {
        super(status, message);
    }

    public static AiException notConfigured() {
        return new AiException(HttpStatus.SERVICE_UNAVAILABLE,
            "AI analysis is not configured on this server.");
    }

    public static AiException timeout() {
        return new AiException(HttpStatus.GATEWAY_TIMEOUT,
            "The AI service took too long to respond. Please try again.");
    }

    public static AiException busy() {
        return new AiException(HttpStatus.SERVICE_UNAVAILABLE,
            "The AI service is busy right now. Please try again in a minute.");
    }

    public static AiException unavailable() {
        return new AiException(HttpStatus.BAD_GATEWAY,
            "The AI service is currently unavailable. Please try again later.");
    }

    public static AiException invalidResponse() {
        return new AiException(HttpStatus.BAD_GATEWAY,
            "The AI service returned an unusable response. Please try again.");
    }

    public static AiException inputTooLarge() {
        return new AiException(HttpStatus.UNPROCESSABLE_ENTITY,
            "The job description and resume are too long to analyze. Shorten the job description and try again.");
    }
}
