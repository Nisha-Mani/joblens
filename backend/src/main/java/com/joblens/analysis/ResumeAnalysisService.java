package com.joblens.analysis;

import com.joblens.analysis.ai.AiException;
import com.joblens.analysis.ai.AiPipeline;
import com.joblens.analysis.ai.AiRequest;
import com.joblens.analysis.dto.AnalysisResponse;
import com.joblens.analysis.dto.AnalysisSummary;
import com.joblens.common.error.ApiException;
import com.joblens.common.error.NotFoundException;
import com.joblens.job.Job;
import com.joblens.job.JobService;
import com.joblens.resume.ResumeService;
import com.joblens.resume.dto.ResumeDetail;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Orchestrates an analysis: load owned data, build a minimal prompt, call the model, validate the
 * answer, store it. Deliberately not transactional: the model call can take many seconds and must
 * not hold a database connection or transaction open. Each repository call commits on its own.
 */
@Service
public class ResumeAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ResumeAnalysisService.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final JobService jobs;
    private final ResumeService resumes;
    private final PromptTemplateService prompts;
    private final AiPipeline pipeline;
    private final AiResponseParser parser;
    private final ResumeAnalysisRepository analyses;
    private final AnalysisRateLimiter rateLimiter;
    private final JsonMapper json;

    public ResumeAnalysisService(JobService jobs, ResumeService resumes, PromptTemplateService prompts,
                                 AiPipeline pipeline, AiResponseParser parser,
                                 ResumeAnalysisRepository analyses, AnalysisRateLimiter rateLimiter,
                                 JsonMapper json) {
        this.jobs = jobs;
        this.resumes = resumes;
        this.prompts = prompts;
        this.pipeline = pipeline;
        this.parser = parser;
        this.analyses = analyses;
        this.rateLimiter = rateLimiter;
        this.json = json;
    }

    public AnalysisResponse analyze(UUID userId, UUID jobId, UUID resumeId) {
        // Ownership and input checks come first so 404s and missing-resume errors cost no quota.
        Job job = jobs.requireOwned(userId, jobId);
        ResumeDetail resume = resumeId != null
            ? resumes.get(userId, resumeId)
            : resumes.findLatest(userId).orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Upload a resume before running an analysis."));

        rateLimiter.acquire(userId);

        AiRequest request = prompts.buildAnalysisRequest(resume.parsed(), job);
        long started = System.nanoTime();
        AiPipeline.Result<AnalysisResult> outcome;
        try {
            outcome = pipeline.run(request, parser::parse);
        } catch (AiException e) {
            log.warn("Analysis failed for job {}: {}", jobId, e.getStatus());
            throw e;
        }
        AnalysisResult result = outcome.value();

        ResumeAnalysis saved = analyses.save(new ResumeAnalysis(userId, jobId, resume.id(), resume.version(),
            result.overallScore(), write(result.matchingSkills()), write(result.missingSkills()),
            write(result.keywordGaps()), result.experienceAssessment(), write(result.suggestions()),
            write(result.interviewTopics()), outcome.model()));
        log.info("Analysis completed: id={} job={} score={} model={} totalMs={}", saved.getId(), jobId,
            saved.getOverallScore(), saved.getModel(), (System.nanoTime() - started) / 1_000_000);
        return toResponse(saved);
    }

    public List<AnalysisSummary> listForJob(UUID userId, UUID jobId) {
        jobs.requireOwned(userId, jobId);
        return analyses.findByJobIdAndUserIdOrderByCreatedAtDesc(jobId, userId).stream()
            .map(a -> new AnalysisSummary(a.getId(), a.getResumeVersion(), a.getOverallScore(),
                a.getModel(), a.getCreatedAt()))
            .toList();
    }

    public AnalysisResponse get(UUID userId, UUID id) {
        return toResponse(analyses.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new NotFoundException("Analysis not found")));
    }

    private AnalysisResponse toResponse(ResumeAnalysis a) {
        return new AnalysisResponse(a.getId(), a.getJobId(), a.getResumeId(), a.getResumeVersion(),
            a.getOverallScore(), read(a.getMatchingSkills()), read(a.getMissingSkills()),
            read(a.getKeywordGaps()), a.getExperienceAssessment(), read(a.getSuggestions()),
            read(a.getInterviewTopics()), a.getModel(), a.getCreatedAt());
    }

    private String write(List<String> values) {
        try {
            return json.writeValueAsString(values);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialise analysis", e);
        }
    }

    private List<String> read(String value) {
        try {
            return json.readValue(value, STRING_LIST);
        } catch (JacksonException e) {
            throw new IllegalStateException("Stored analysis is corrupt", e);
        }
    }
}
