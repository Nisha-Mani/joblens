package com.joblens.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joblens.analysis.ai.AiException;
import com.joblens.analysis.ai.AiPurpose;
import com.joblens.analysis.ai.AiProperties;
import com.joblens.analysis.ai.AiRequest;
import com.joblens.analysis.ai.AiResponse;
import com.joblens.analysis.ai.OpenAiClient;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Exercises the real HTTP client against a local stub, so no paid API is ever called. */
class OpenAiClientTest {

    private record Stub(int status, String body, long delayMs) { }
    private record Recorded(String path, String authorization, String body) { }

    private final JsonMapper json = JsonMapper.builder().build();
    private final Deque<Stub> responses = new ArrayDeque<>();
    private final List<Recorded> requests = new ArrayList<>();
    private HttpServer server;

    private static final AiRequest REQUEST = new AiRequest(AiPurpose.RESUME_JOB_ANALYSIS, "system text",
        "user text", "resume_job_analysis", "{\"type\":\"object\",\"properties\":{}}");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Recorded(exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"), body));
            Stub stub = responses.isEmpty() ? new Stub(500, "{}", 0) : responses.poll();
            try {
                Thread.sleep(stub.delayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = stub.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(stub.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private OpenAiClient client(String apiKey, int timeoutSeconds) {
        return new OpenAiClient(new AiProperties("openai", apiKey, "test-model",
            "http://localhost:" + server.getAddress().getPort(), timeoutSeconds, 800, 20), json);
    }

    private static String completion(String content, String finishReason) {
        return """
            {"model":"test-model-2026","choices":[{"finish_reason":"%s","message":{"content":%s}}],
             "usage":{"prompt_tokens":100,"completion_tokens":50}}"""
            .formatted(finishReason, JsonMapper.builder().build().writeValueAsString(content));
    }

    @Test
    void sendsStructuredOutputRequestAndReturnsContent() {
        responses.add(new Stub(200, completion("{\"ok\":true}", "stop"), 0));

        AiResponse response = client("sk-test", 5).complete(REQUEST);

        assertThat(response.content()).isEqualTo("{\"ok\":true}");
        assertThat(response.model()).isEqualTo("test-model-2026");
        assertThat(requests).hasSize(1);
        Recorded sent = requests.get(0);
        assertThat(sent.path()).isEqualTo("/v1/chat/completions");
        assertThat(sent.authorization()).isEqualTo("Bearer sk-test");
        JsonNode body = json.readTree(sent.body());
        assertThat(body.path("model").asString()).isEqualTo("test-model");
        assertThat(body.path("max_completion_tokens").asInt()).isEqualTo(800);
        assertThat(body.path("messages").get(0).path("role").asString()).isEqualTo("system");
        assertThat(body.path("messages").get(1).path("content").asString()).isEqualTo("user text");
        assertThat(body.path("response_format").path("type").asString()).isEqualTo("json_schema");
        assertThat(body.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(body.path("response_format").path("json_schema").path("name").asString()).isEqualTo("resume_job_analysis");
    }

    @Test
    void missingApiKeyFailsBeforeAnyRequest() {
        assertThatThrownBy(() -> client("", 5).complete(REQUEST))
            .isInstanceOf(AiException.class)
            .hasMessageContaining("not configured");
        assertThat(requests).isEmpty();
    }

    @Test
    void rejectedCredentialsReportAsNotConfiguredWithoutLeakingProviderDetails() {
        responses.add(new Stub(401, "{\"error\":{\"message\":\"Incorrect API key provided: sk-secret\"}}", 0));
        assertThatThrownBy(() -> client("sk-secret", 5).complete(REQUEST))
            .isInstanceOf(AiException.class)
            .satisfies(e -> {
                assertThat(((AiException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(e.getMessage()).doesNotContain("sk-secret");
            });
        assertThat(requests).hasSize(1);
    }

    @Test
    void rateLimitIsRetriedOnceThenSucceeds() {
        responses.add(new Stub(429, "{}", 0));
        responses.add(new Stub(200, completion("{}", "stop"), 0));
        assertThat(client("k", 5).complete(REQUEST).content()).isEqualTo("{}");
        assertThat(requests).hasSize(2);
    }

    @Test
    void persistentRateLimitSurfacesAsBusy() {
        responses.add(new Stub(429, "{}", 0));
        responses.add(new Stub(429, "{}", 0));
        assertThatThrownBy(() -> client("k", 5).complete(REQUEST))
            .isInstanceOf(AiException.class).hasMessageContaining("busy");
        assertThat(requests).hasSize(2);
    }

    @Test
    void serverErrorsAreRetriedThenReportedAsUnavailable() {
        responses.add(new Stub(503, "{}", 0));
        responses.add(new Stub(500, "{}", 0));
        assertThatThrownBy(() -> client("k", 5).complete(REQUEST))
            .isInstanceOf(AiException.class)
            .satisfies(e -> assertThat(((AiException) e).getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY))
            .hasMessageContaining("unavailable");
        assertThat(requests).hasSize(2);
    }

    @Test
    void contextLengthErrorIsNotRetriedAndAsksUserToShorten() {
        responses.add(new Stub(400, "{\"error\":{\"code\":\"context_length_exceeded\"}}", 0));
        assertThatThrownBy(() -> client("k", 5).complete(REQUEST))
            .isInstanceOf(AiException.class)
            .satisfies(e -> assertThat(((AiException) e).getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
            .hasMessageContaining("too long");
        assertThat(requests).hasSize(1);
    }

    @Test
    void slowResponsesTimeOut() {
        responses.add(new Stub(200, completion("{}", "stop"), 3000));
        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> client("k", 1).complete(REQUEST))
            .isInstanceOf(AiException.class)
            .satisfies(e -> assertThat(((AiException) e).getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
        assertThat(System.currentTimeMillis() - start).isLessThan(2900);
    }

    @Test
    void unreachableProviderReportsUnavailable() {
        int port = server.getAddress().getPort();
        server.stop(0);
        OpenAiClient client = new OpenAiClient(new AiProperties("openai", "k", "m",
            "http://localhost:" + port, 2, 800, 20), json);
        assertThatThrownBy(() -> client.complete(REQUEST))
            .isInstanceOf(AiException.class).hasMessageContaining("unavailable");
    }

    @Test
    void truncatedOutputIsTreatedAsInvalid() {
        responses.add(new Stub(200, completion("{\"overallSc", "length"), 0));
        assertThatThrownBy(() -> client("k", 5).complete(REQUEST))
            .isInstanceOf(AiException.class).hasMessageContaining("unusable");
    }

    @Test
    void refusalsAreTreatedAsInvalid() {
        responses.add(new Stub(200, "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":null,\"refusal\":\"I cannot help\"}}]}", 0));
        assertThatThrownBy(() -> client("k", 5).complete(REQUEST))
            .isInstanceOf(AiException.class).hasMessageContaining("unusable");
    }

    @Test
    void emptyOrMalformedEnvelopesAreTreatedAsInvalid() {
        responses.add(new Stub(200, "{\"choices\":[]}", 0));
        assertThatThrownBy(() -> client("k", 5).complete(REQUEST)).isInstanceOf(AiException.class);
        responses.add(new Stub(200, "<html>gateway</html>", 0));
        assertThatThrownBy(() -> client("k", 5).complete(REQUEST)).isInstanceOf(AiException.class);
    }
}
