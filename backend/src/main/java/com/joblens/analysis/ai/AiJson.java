package com.joblens.analysis.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Tolerant reading of model output into a JSON object; shared by every response parser. */
public final class AiJson {

    private static final Logger log = LoggerFactory.getLogger(AiJson.class);

    private AiJson() { }

    /**
     * Accepts plain JSON, markdown-fenced JSON, or JSON surrounded by chatter.
     *
     * @throws AiException (unusable response) if no JSON object can be found
     */
    public static JsonNode readObject(JsonMapper json, String raw) {
        if (raw == null || raw.isBlank()) {
            throw reject("empty response");
        }
        String candidate = stripFences(raw.strip());
        JsonNode direct = tryRead(json, candidate);
        if (direct != null) {
            return direct;
        }
        int start = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');
        if (start >= 0 && end > start) {
            JsonNode extracted = tryRead(json, candidate.substring(start, end + 1));
            if (extracted != null) {
                return extracted;
            }
        }
        throw reject("not a JSON object");
    }

    /** Logs why output was rejected (never the output itself) and returns the user-facing error. */
    public static AiException reject(String reason) {
        log.warn("AI response rejected: {}", reason);
        return AiException.invalidResponse();
    }

    public static boolean isInvalidResponse(AiException e) {
        return e.getStatus() == org.springframework.http.HttpStatus.BAD_GATEWAY && e.getMessage().contains("unusable");
    }

    private static JsonNode tryRead(JsonMapper json, String text) {
        try {
            JsonNode node = json.readTree(text);
            return node != null && node.isObject() ? node : null;
        } catch (JacksonException e) {
            return null;
        }
    }

    private static String stripFences(String value) {
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            String body = firstNewline >= 0 ? value.substring(firstNewline + 1) : value.substring(3);
            return body.endsWith("```") ? body.substring(0, body.length() - 3).strip() : body.strip();
        }
        return value;
    }
}
