package com.joblens.analysis.ai;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chat Completions client using structured outputs. Converts every failure mode into an
 * {@link AiException} with a user-safe message. Prompts and completions are never logged.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private static final int MAX_ATTEMPTS = 2;

    private final AiProperties properties;
    private final JsonMapper json;
    private final RestClient restClient;

    public OpenAiClient(AiProperties properties, JsonMapper json) {
        this.properties = properties;
        this.json = json;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }

    @Override
    public AiResponse complete(AiRequest request) {
        if (!properties.hasApiKey()) {
            log.error("AI request refused: OPENAI_API_KEY is not set");
            throw AiException.notConfigured();
        }
        String body = requestBody(request);
        AiException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long started = System.nanoTime();
            try {
                return attemptOnce(body, started);
            } catch (AiException e) {
                last = e;
                if (!isRetryable(e) || attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("AI call failed (attempt {}/{}), retrying", attempt, MAX_ATTEMPTS);
                pause(400L * attempt);
            }
        }
        throw last;
    }

    private AiResponse attemptOnce(String body, long started) {
        RawResult result;
        try {
            result = restClient.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, res) -> new RawResult(res.getStatusCode(),
                    new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (ResourceAccessException e) {
            throw translateIo(e);
        }
        long millis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        if (result.status().isError()) {
            throw translateStatus(result, millis);
        }
        return parse(result.body(), millis);
    }

    private AiResponse parse(String body, long millis) {
        try {
            JsonNode root = json.readTree(body);
            JsonNode choice = root.path("choices").path(0);
            String finish = choice.path("finish_reason").asString("");
            JsonNode message = choice.path("message");
            if (message.hasNonNull("refusal")) {
                log.warn("AI model refused the request");
                throw AiException.invalidResponse();
            }
            if ("length".equals(finish)) {
                log.warn("AI output truncated by the token limit");
                throw AiException.invalidResponse();
            }
            String content = message.path("content").asString("");
            if (content.isBlank()) {
                log.warn("AI response had no content");
                throw AiException.invalidResponse();
            }
            String model = root.path("model").asString(properties.model());
            log.info("AI call succeeded: model={} latencyMs={} promptTokens={} completionTokens={}", model, millis,
                root.path("usage").path("prompt_tokens").asInt(-1),
                root.path("usage").path("completion_tokens").asInt(-1));
            return new AiResponse(content, model);
        } catch (JacksonException e) {
            log.warn("AI response envelope was not valid JSON");
            throw AiException.invalidResponse();
        }
    }

    private AiException translateStatus(RawResult result, long millis) {
        int status = result.status().value();
        log.warn("AI provider returned HTTP {} after {} ms", status, millis);
        if (status == 401 || status == 403) {
            log.error("AI provider rejected the credentials");
            return AiException.notConfigured();
        }
        if (status == 429) {
            return AiException.busy();
        }
        if (status == 400 && result.body().contains("context_length_exceeded")) {
            return AiException.inputTooLarge();
        }
        if (status >= 500) {
            return AiException.unavailable();
        }
        return AiException.invalidResponse();
    }

    private AiException translateIo(ResourceAccessException e) {
        Throwable cause = e.getCause();
        if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
            log.warn("AI call timed out after {}s", properties.timeoutSeconds());
            return AiException.timeout();
        }
        if (cause instanceof ConnectException || cause instanceof IOException) {
            log.warn("AI provider unreachable: {}", cause.getClass().getSimpleName());
            return AiException.unavailable();
        }
        return AiException.unavailable();
    }

    private static boolean isRetryable(AiException e) {
        return e.getStatus().value() == 503 && e.getMessage().contains("busy")
            || e.getStatus().value() == 502 && e.getMessage().contains("unavailable");
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String requestBody(AiRequest request) {
        try {
            Map<String, Object> body = Map.of(
                "model", properties.model(),
                "temperature", 0.2,
                "max_completion_tokens", properties.maxOutputTokens(),
                "messages", List.of(
                    Map.of("role", "system", "content", request.system()),
                    Map.of("role", "user", "content", request.user())),
                "response_format", Map.of(
                    "type", "json_schema",
                    "json_schema", Map.of(
                        "name", request.schemaName(),
                        "strict", true,
                        "schema", json.readTree(request.schemaJson()))));
            return json.writeValueAsString(body);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not build AI request", e);
        }
    }

    private record RawResult(HttpStatusCode status, String body) { }
}
