package com.joblens.analysis.ai;

import com.joblens.analysis.PromptTemplateService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deterministic stand-in for an LLM, used for local development, demos, tests and E2E runs so
 * nothing depends on a paid API. It reads the same prompt a real model would receive and answers
 * in the same schema, so the parsing, validation and storage pipeline is fully exercised.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiClient implements AiClient {

    private static final String MODEL = "mock-analyzer";

    private final JsonMapper json;

    public MockAiClient(JsonMapper json) {
        this.json = json;
    }

    @Override
    public AiResponse complete(AiRequest request) {
        JsonNode resume = PromptTemplateService.section(json, request.user(), PromptTemplateService.RESUME_TAG);
        JsonNode job = PromptTemplateService.section(json, request.user(), PromptTemplateService.JOB_TAG);

        Set<String> resumeSkills = lowercase(resume.path("skills"));
        List<String> jobSkills = strings(job.path("detectedSkills"));

        List<String> matching = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String skill : jobSkills) {
            (resumeSkills.contains(skill.toLowerCase(Locale.ROOT)) ? matching : missing).add(skill);
        }
        int score = jobSkills.isEmpty() ? 50 : Math.round(100f * matching.size() / jobSkills.size());
        int years = job.path("requiredYears").asInt(0);

        var result = json.createObjectNode();
        result.put("overallScore", score);
        result.set("matchingSkills", json.valueToTree(matching));
        result.set("missingSkills", json.valueToTree(missing));
        result.set("keywordGaps", json.valueToTree(missing.stream().limit(5).toList()));
        result.put("experienceAssessment", years > 0
            ? "The role asks for about " + years + " years of experience; compare this with the experience on your resume."
            : "No specific years of experience were detected in the job description.");
        result.set("suggestions", json.valueToTree(missing.isEmpty()
            ? List.of("Your listed skills cover the technologies detected in this job description.")
            : missing.stream().limit(5).map(s -> "Add concrete evidence of " + s + " to your resume if you have it.").toList()));
        result.set("interviewTopics", json.valueToTree(jobSkills.stream().limit(6).toList()));
        return new AiResponse(result.toString(), MODEL);
    }

    private static Set<String> lowercase(JsonNode array) {
        return strings(array).stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(n -> values.add(n.asString()));
        return values;
    }
}
