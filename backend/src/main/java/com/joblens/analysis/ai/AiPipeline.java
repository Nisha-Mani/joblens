package com.joblens.analysis.ai;

import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs a request through the model and a validating parser. Unusable output is retried exactly
 * once (models occasionally slip); every other failure is surfaced immediately. Used by every AI
 * feature so retry and failure behaviour is identical and defined in one place.
 */
@Component
public class AiPipeline {

    private static final Logger log = LoggerFactory.getLogger(AiPipeline.class);

    private final AiClient client;

    public AiPipeline(AiClient client) {
        this.client = client;
    }

    public record Result<T>(T value, String model) { }

    public <T> Result<T> run(AiRequest request, Function<String, T> parser) {
        try {
            return attempt(request, parser);
        } catch (AiException first) {
            if (!AiJson.isInvalidResponse(first)) {
                throw first;
            }
            log.warn("Invalid AI output for {}, retrying once", request.purpose());
            return attempt(request, parser);
        }
    }

    private <T> Result<T> attempt(AiRequest request, Function<String, T> parser) {
        AiResponse response = client.complete(request);
        return new Result<>(parser.apply(response.content()), response.model());
    }
}
