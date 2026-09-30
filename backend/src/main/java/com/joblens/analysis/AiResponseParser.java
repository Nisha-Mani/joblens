package com.joblens.analysis;

import com.joblens.analysis.ai.AiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns raw model output into a trusted {@link AnalysisResult}. Structure, types and ranges are
 * validated strictly (anything wrong is rejected); sizes are bounded by trimming so stored
 * documents stay small. Failure reasons are logged, but never the model output itself, which can
 * contain resume content.
 */
@Component
public class AiResponseParser {

    private static final Logger log = LoggerFactory.getLogger(AiResponseParser.class);

    static final int MAX_SKILLS = 50;
    static final int MAX_SKILL_LENGTH = 60;
    static final int MAX_SUGGESTIONS = 15;
    static final int MAX_SUGGESTION_LENGTH = 500;
    static final int MAX_TOPICS = 15;
    static final int MAX_TOPIC_LENGTH = 120;
    static final int MAX_ASSESSMENT_LENGTH = 2000;

    private final JsonMapper json;

    public AiResponseParser(JsonMapper json) {
        this.json = json;
    }

    public AnalysisResult parse(String raw) {
        JsonNode root = readObject(raw);

        int score = score(root.get("overallScore"));
        List<String> matching = list(root, "matchingSkills", MAX_SKILLS, MAX_SKILL_LENGTH);
        List<String> missing = list(root, "missingSkills", MAX_SKILLS, MAX_SKILL_LENGTH);
        List<String> gaps = list(root, "keywordGaps", MAX_SKILLS, MAX_SKILL_LENGTH);
        List<String> suggestions = list(root, "suggestions", MAX_SUGGESTIONS, MAX_SUGGESTION_LENGTH);
        List<String> topics = list(root, "interviewTopics", MAX_TOPICS, MAX_TOPIC_LENGTH);
        String assessment = text(root, "experienceAssessment", MAX_ASSESSMENT_LENGTH);

        if (overlaps(matching, missing)) {
            throw reject("a skill is listed as both matching and missing");
        }
        return new AnalysisResult(score, matching, missing, gaps, assessment, suggestions, topics);
    }

    private JsonNode readObject(String raw) {
        if (raw == null || raw.isBlank()) {
            throw reject("empty response");
        }
        String candidate = stripFences(raw.strip());
        try {
            JsonNode node = json.readTree(candidate);
            if (node != null && node.isObject()) {
                return node;
            }
        } catch (JacksonException ignored) {
            // fall through to the lenient extraction below
        }
        int start = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                JsonNode node = json.readTree(candidate.substring(start, end + 1));
                if (node != null && node.isObject()) {
                    return node;
                }
            } catch (JacksonException ignored) {
                // reported below
            }
        }
        throw reject("not a JSON object");
    }

    private static String stripFences(String value) {
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            String body = firstNewline >= 0 ? value.substring(firstNewline + 1) : value.substring(3);
            return body.endsWith("```") ? body.substring(0, body.length() - 3).strip() : body.strip();
        }
        return value;
    }

    private int score(JsonNode node) {
        if (node == null || !node.isNumber()) {
            throw reject("overallScore missing or not a number");
        }
        double value = node.asDouble();
        if (value != Math.rint(value)) {
            throw reject("overallScore is not a whole number");
        }
        if (value < 0 || value > 100) {
            throw reject("overallScore out of range");
        }
        return (int) value;
    }

    private List<String> list(JsonNode root, String field, int maxItems, int maxLength) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) {
            throw reject(field + " missing or not an array");
        }
        // Keyed by lower-case text so case-variant duplicates collapse; first spelling wins.
        Map<String, String> unique = new LinkedHashMap<>();
        for (JsonNode item : node) {
            if (!item.isString()) {
                throw reject(field + " contains a non-string item");
            }
            String value = bound(item.asString().strip(), maxLength);
            if (!value.isEmpty()) {
                unique.putIfAbsent(value.toLowerCase(Locale.ROOT), value);
            }
        }
        List<String> values = new ArrayList<>(unique.values());
        return values.size() > maxItems ? List.copyOf(values.subList(0, maxItems)) : List.copyOf(values);
    }

    private String text(JsonNode root, String field, int maxLength) {
        JsonNode node = root.get(field);
        if (node == null || !node.isString() || node.asString().isBlank()) {
            throw reject(field + " missing or empty");
        }
        return bound(node.asString().strip(), maxLength);
    }

    private static String bound(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1).stripTrailing() + "…";
    }

    private static boolean overlaps(List<String> a, List<String> b) {
        List<String> lower = a.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
        return b.stream().anyMatch(s -> lower.contains(s.toLowerCase(Locale.ROOT)));
    }

    private AiException reject(String reason) {
        log.warn("AI response rejected: {}", reason);
        return AiException.invalidResponse();
    }
}
