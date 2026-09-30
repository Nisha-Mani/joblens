package com.joblens.interview;

import com.joblens.analysis.ai.AiJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates model-generated interview questions. Every item needs a non-blank question and a
 * known category and difficulty; one bad item invalidates the whole response rather than
 * silently storing partial or wrongly-typed data. Sizes are bounded by trimming and truncation.
 */
@Component
public class InterviewQuestionsParser {

    static final int MAX_QUESTIONS = 15;
    static final int MAX_QUESTION_LENGTH = 500;
    static final int MAX_SKILLS = 5;
    static final int MAX_SKILL_LENGTH = 40;

    public record GeneratedQuestion(String question, InterviewCategory category, Difficulty difficulty,
                                    List<String> skills) { }

    private final JsonMapper json;

    public InterviewQuestionsParser(JsonMapper json) {
        this.json = json;
    }

    public List<GeneratedQuestion> parse(String raw) {
        JsonNode root = AiJson.readObject(json, raw);
        JsonNode items = root.get("questions");
        if (items == null || !items.isArray()) {
            throw AiJson.reject("questions missing or not an array");
        }
        // Keyed by normalised text so repeats collapse; the first occurrence wins.
        Map<String, GeneratedQuestion> unique = new LinkedHashMap<>();
        for (JsonNode item : items) {
            if (!item.isObject()) {
                throw AiJson.reject("a question is not an object");
            }
            GeneratedQuestion question = new GeneratedQuestion(
                text(item),
                enumValue(item, "category", InterviewCategory.class),
                enumValue(item, "difficulty", Difficulty.class),
                skills(item.get("skills")));
            unique.putIfAbsent(normalize(question.question()), question);
        }
        if (unique.isEmpty()) {
            throw AiJson.reject("no questions returned");
        }
        List<GeneratedQuestion> all = new ArrayList<>(unique.values());
        return all.size() > MAX_QUESTIONS ? List.copyOf(all.subList(0, MAX_QUESTIONS)) : List.copyOf(all);
    }

    public static String normalize(String question) {
        return question.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String text(JsonNode item) {
        JsonNode node = item.get("question");
        if (node == null || !node.isString() || node.asString().isBlank()) {
            throw AiJson.reject("a question is missing its text");
        }
        String value = node.asString().strip();
        return value.length() <= MAX_QUESTION_LENGTH ? value
            : value.substring(0, MAX_QUESTION_LENGTH - 1).stripTrailing() + "…";
    }

    private static <E extends Enum<E>> E enumValue(JsonNode item, String field, Class<E> type) {
        JsonNode node = item.get(field);
        if (node == null || !node.isString()) {
            throw AiJson.reject(field + " missing or not a string");
        }
        String normalised = node.asString().strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return Enum.valueOf(type, normalised);
        } catch (IllegalArgumentException e) {
            throw AiJson.reject("unknown " + field);
        }
    }

    private static List<String> skills(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw AiJson.reject("skills missing or not an array");
        }
        List<String> skills = new ArrayList<>();
        for (JsonNode skill : node) {
            if (!skill.isString()) {
                throw AiJson.reject("skills contains a non-string item");
            }
            String value = skill.asString().strip();
            if (value.length() > MAX_SKILL_LENGTH) {
                value = value.substring(0, MAX_SKILL_LENGTH);
            }
            if (!value.isEmpty() && skills.stream().noneMatch(s -> s.equalsIgnoreCase(skill.asString().strip()))
                && skills.size() < MAX_SKILLS) {
                skills.add(value);
            }
        }
        return List.copyOf(skills);
    }
}
