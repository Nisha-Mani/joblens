package com.joblens.analysis;

import com.joblens.analysis.ai.AiException;
import com.joblens.analysis.ai.AiPurpose;
import com.joblens.analysis.ai.AiRequest;
import com.joblens.job.Job;
import com.joblens.resume.ParsedResume;
import com.joblens.resume.TechnologyDictionary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the model prompt. This is the single place that decides what leaves the system: resume
 * content is minimised (no name, email or phone), every field is size-bounded, and user-controlled
 * text cannot close the delimiter tags that mark it as data.
 */
@Service
public class PromptTemplateService {

    public static final String RESUME_TAG = "resume";
    public static final String JOB_TAG = "job";

    static final int MAX_SKILLS = 100;
    static final int MAX_EXPERIENCE_CHARS = 6000;
    static final int MAX_ENTRY_CHARS = 1000;
    static final int MAX_SUMMARY_CHARS = 800;
    static final int MAX_OTHER_ENTRIES = 10;
    static final int MAX_JOB_DESCRIPTION_CHARS = 12000;
    /** Hard ceiling on the assembled prompt (~10k tokens at 4 chars/token). */
    static final int MAX_PROMPT_CHARS = 40000;

    private static final Pattern YEARS = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*\\+?\\s*(?:-\\s*\\d{1,2}\\s*)?years?", Pattern.CASE_INSENSITIVE);

    private final JsonMapper json;
    private final String systemPrompt;
    private final String analysisSchema;

    public PromptTemplateService(JsonMapper json) {
        this.json = json;
        this.systemPrompt = load("ai/resume-job-analysis.txt");
        this.analysisSchema = load("ai/analysis-schema.json");
    }

    public AiRequest buildAnalysisRequest(ParsedResume resume, Job job) {
        String resumeJson = toJson(resumeInput(resume));
        String jobJson = toJson(jobInput(job));
        String user = "<" + RESUME_TAG + ">\n" + resumeJson + "\n</" + RESUME_TAG + ">\n"
            + "<" + JOB_TAG + ">\n" + jobJson + "\n</" + JOB_TAG + ">";

        if (systemPrompt.length() + user.length() > MAX_PROMPT_CHARS) {
            throw AiException.inputTooLarge();
        }
        return new AiRequest(AiPurpose.RESUME_JOB_ANALYSIS, systemPrompt, user,
            "resume_job_analysis", analysisSchema);
    }

    /** Extracts the JSON document between {@code <tag>} and {@code </tag>} of a prompt built here. */
    public static JsonNode section(JsonMapper json, String prompt, String tag) {
        Matcher matcher = Pattern.compile("<" + tag + ">\\s*(.*?)\\s*</" + tag + ">", Pattern.DOTALL).matcher(prompt);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Prompt has no <" + tag + "> section");
        }
        try {
            return json.readTree(matcher.group(1));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Prompt section <" + tag + "> is not valid JSON", e);
        }
    }

    private Object resumeInput(ParsedResume resume) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("summary", clean(resume.summary(), MAX_SUMMARY_CHARS));
        input.put("skills", resume.skills().stream().limit(MAX_SKILLS).map(s -> clean(s, 60)).toList());
        input.put("experience", budget(resume.experience(), MAX_EXPERIENCE_CHARS));
        input.put("education", limit(resume.education(), 5));
        input.put("projects", limit(resume.projects(), 5));
        input.put("certifications", limit(resume.certifications(), MAX_OTHER_ENTRIES));
        return input;
    }

    private Object jobInput(Job job) {
        String description = clean(job.getJobDescription(), MAX_JOB_DESCRIPTION_CHARS);
        List<String> detected = TechnologyDictionary.detect(job.getJobDescription());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("title", clean(job.getTitle(), 150));
        input.put("company", clean(job.getCompany(), 150));
        input.put("description", description);
        input.put("detectedSkills", detected);
        input.put("requiredYears", requiredYears(job.getJobDescription()));
        return input;
    }

    static int requiredYears(String description) {
        Matcher matcher = YEARS.matcher(description);
        int max = 0;
        while (matcher.find()) {
            max = Math.max(max, Integer.parseInt(matcher.group(1)));
        }
        return max <= 40 ? max : 0;
    }

    private static List<String> budget(List<String> entries, int totalChars) {
        List<String> kept = new ArrayList<>();
        int used = 0;
        for (String entry : entries) {
            String bounded = clean(entry, MAX_ENTRY_CHARS);
            if (used + bounded.length() > totalChars) {
                break;
            }
            kept.add(bounded);
            used += bounded.length();
        }
        return kept;
    }

    private static List<String> limit(List<String> entries, int max) {
        return entries.stream().limit(max).map(e -> clean(e, MAX_ENTRY_CHARS / 2)).toList();
    }

    /** Bounds length and stops user text from forging or closing our delimiter tags. */
    static String clean(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String safe = value.replace('<', '‹').strip();
        return safe.length() <= maxChars ? safe : safe.substring(0, maxChars);
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialise prompt input", e);
        }
    }

    private static String load(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing prompt resource " + path, e);
        }
    }
}
