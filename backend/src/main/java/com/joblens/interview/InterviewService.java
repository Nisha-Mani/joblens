package com.joblens.interview;

import com.joblens.analysis.AnalysisRateLimiter;
import com.joblens.analysis.PromptTemplateService;
import com.joblens.analysis.ai.AiException;
import com.joblens.analysis.ai.AiPipeline;
import com.joblens.analysis.ai.AiRequest;
import com.joblens.common.error.NotFoundException;
import com.joblens.common.web.PageResponse;
import com.joblens.interview.dto.CustomQuestionRequest;
import com.joblens.interview.dto.PrepUpdateRequest;
import com.joblens.interview.dto.QuestionResponse;
import com.joblens.job.Job;
import com.joblens.job.JobService;
import com.joblens.resume.ParsedResume;
import com.joblens.resume.ResumeService;
import com.joblens.resume.dto.ResumeDetail;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Interview preparation. Generation reuses the shared AI pipeline and rate limit; it is not
 * transactional because the model call is slow. Questions belong to a job, and everything is
 * scoped to the owning user.
 */
@Service
public class InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    static final int MAX_PAGE_SIZE = 100;

    private final JobService jobs;
    private final ResumeService resumes;
    private final PromptTemplateService prompts;
    private final AiPipeline pipeline;
    private final InterviewQuestionsParser parser;
    private final InterviewQuestionRepository questions;
    private final AnalysisRateLimiter rateLimiter;
    private final JsonMapper json;

    public InterviewService(JobService jobs, ResumeService resumes, PromptTemplateService prompts,
                            AiPipeline pipeline, InterviewQuestionsParser parser,
                            InterviewQuestionRepository questions, AnalysisRateLimiter rateLimiter,
                            JsonMapper json) {
        this.jobs = jobs;
        this.resumes = resumes;
        this.prompts = prompts;
        this.pipeline = pipeline;
        this.parser = parser;
        this.questions = questions;
        this.rateLimiter = rateLimiter;
        this.json = json;
    }

    /** Generates questions for a job and stores the ones not already present. Returns the new ones. */
    public List<QuestionResponse> generate(UUID userId, UUID jobId, UUID resumeId) {
        Job job = jobs.requireOwned(userId, jobId);
        // A resume sharpens the questions but is optional: role and technical questions stand alone.
        ParsedResume resume = resumeId != null
            ? resumes.get(userId, resumeId).parsed()
            : resumes.findLatest(userId).map(ResumeDetail::parsed).orElse(null);

        rateLimiter.acquire(userId);

        AiRequest request = prompts.buildInterviewRequest(resume, job);
        AiPipeline.Result<List<InterviewQuestionsParser.GeneratedQuestion>> outcome;
        try {
            outcome = pipeline.run(request, parser::parse);
        } catch (AiException e) {
            log.warn("Question generation failed for job {}: {}", jobId, e.getStatus());
            throw e;
        }

        Set<String> existing = new HashSet<>();
        questions.findByJobIdAndUserId(jobId, userId)
            .forEach(q -> existing.add(InterviewQuestionsParser.normalize(q.getQuestion())));

        List<InterviewQuestion> fresh = outcome.value().stream()
            .filter(q -> existing.add(InterviewQuestionsParser.normalize(q.question())))
            .map(q -> new InterviewQuestion(userId, job, q.question(), q.category(), q.difficulty(),
                write(q.skills()), true))
            .toList();
        List<InterviewQuestion> saved = questions.saveAll(fresh);
        log.info("Interview questions generated: job={} new={} model={}", jobId, saved.size(), outcome.model());
        return saved.stream().map(this::toResponse).toList();
    }

    @Transactional
    public QuestionResponse addCustom(UUID userId, CustomQuestionRequest request) {
        Job job = jobs.requireOwned(userId, request.jobId());
        InterviewQuestion saved = questions.save(new InterviewQuestion(userId, job, request.question().strip(),
            request.category(), request.difficulty(), "[]", false));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionResponse> search(UUID userId, UUID jobId, InterviewCategory category,
                                                 PrepStatus status, int page, int size) {
        Specification<InterviewQuestion> spec = (root, q, cb) -> cb.equal(root.get("userId"), userId);
        if (jobId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("job").get("id"), jobId));
        }
        if (category != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("category"), category));
        }
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
        return PageResponse.of(questions.findAll(spec, pageable), this::toResponse);
    }

    @Transactional
    public QuestionResponse updatePrep(UUID userId, UUID id, PrepUpdateRequest request) {
        InterviewQuestion question = find(userId, id);
        String notes = request.notes() == null || request.notes().isBlank() ? null : request.notes().strip();
        question.updatePrep(notes, request.status());
        return toResponse(questions.saveAndFlush(question));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        questions.delete(find(userId, id));
    }

    private InterviewQuestion find(UUID userId, UUID id) {
        return questions.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new NotFoundException("Question not found"));
    }

    private QuestionResponse toResponse(InterviewQuestion q) {
        return new QuestionResponse(q.getId(), q.getJob().getId(), q.getJob().getCompany(), q.getJob().getTitle(),
            q.getQuestion(), q.getCategory(), q.getDifficulty(), read(q.getSkills()), q.getNotes(), q.getStatus(),
            q.isGenerated(), q.getCreatedAt(), q.getUpdatedAt());
    }

    private String write(List<String> values) {
        try {
            return json.writeValueAsString(values);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialise skills", e);
        }
    }

    private List<String> read(String value) {
        try {
            return json.readValue(value, STRING_LIST);
        } catch (JacksonException e) {
            throw new IllegalStateException("Stored question skills are corrupt", e);
        }
    }
}
