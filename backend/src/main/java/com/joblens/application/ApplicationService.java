package com.joblens.application;

import com.joblens.application.dto.ApplicationDetail;
import com.joblens.application.dto.ApplicationRequest;
import com.joblens.application.dto.ApplicationSummary;
import com.joblens.common.error.ApiException;
import com.joblens.common.error.ConflictException;
import com.joblens.common.error.NotFoundException;
import com.joblens.common.web.PageResponse;
import com.joblens.job.Job;
import com.joblens.job.JobService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    static final int MAX_PAGE_SIZE = 100;
    /** Client-facing sort keys mapped to entity property paths; anything else is rejected. */
    private static final Map<String, String> SORTABLE = Map.of(
        "createdAt", "createdAt",
        "updatedAt", "updatedAt",
        "appliedAt", "appliedAt",
        "interviewDate", "interviewDate",
        "status", "status",
        "company", "job.company");

    private final ApplicationRepository applications;
    private final StatusChangeRepository history;
    private final JobService jobs;

    public ApplicationService(ApplicationRepository applications, StatusChangeRepository history,
                              JobService jobs) {
        this.applications = applications;
        this.history = history;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationSummary> search(UUID userId, String query, ApplicationStatus status,
                                                   UUID jobId, String sortBy, String direction, int page, int size) {
        String property = SORTABLE.get(sortBy);
        if (property == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot sort by '" + sortBy + "'");
        }
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        // Rows without a value (e.g. no interview yet) always sort last; id breaks ties for stable paging.
        Sort sort = Sort.by(new Sort.Order(dir, property).nullsLast()).and(Sort.by(Sort.Direction.ASC, "id"));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
        return PageResponse.of(applications.findAll(filter(userId, query, status, jobId), pageable),
            ApplicationSummary::from);
    }

    @Transactional(readOnly = true)
    public ApplicationDetail get(UUID userId, UUID id) {
        Application application = find(userId, id);
        return detail(application);
    }

    @Transactional
    public ApplicationDetail create(UUID userId, ApplicationRequest request) {
        if (request.jobId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Job is required");
        }
        Job job = jobs.requireOwned(userId, request.jobId());
        if (applications.existsByJobId(job.getId())) {
            throw new ConflictException("This job already has an application");
        }
        Application application = new Application(userId, job, request.status());
        applyFields(application, request);
        try {
            application = applications.saveAndFlush(application);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("This job already has an application");
        }
        history.save(new StatusChange(application.getId(), null, application.getStatus()));
        log.info("Application created: id={} status={}", application.getId(), application.getStatus());
        return detail(application);
    }

    @Transactional
    public ApplicationDetail update(UUID userId, UUID id, ApplicationRequest request) {
        Application application = find(userId, id);
        ApplicationStatus before = application.getStatus();
        application.setStatus(request.status());
        applyFields(application, request);
        recordChange(application, before);
        return detail(applications.saveAndFlush(application));
    }

    @Transactional
    public ApplicationDetail changeStatus(UUID userId, UUID id, ApplicationStatus status) {
        Application application = find(userId, id);
        ApplicationStatus before = application.getStatus();
        application.setStatus(status);
        applyAppliedDate(application);
        recordChange(application, before);
        return detail(applications.saveAndFlush(application));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        applications.delete(find(userId, id));
        log.info("Application deleted: id={}", id);
    }

    private Application find(UUID userId, UUID id) {
        return applications.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new NotFoundException("Application not found"));
    }

    private ApplicationDetail detail(Application application) {
        return ApplicationDetail.from(application,
            history.findByApplicationIdOrderByChangedAtAsc(application.getId()));
    }

    private void applyFields(Application application, ApplicationRequest request) {
        application.setInterviewDate(request.interviewDate());
        application.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().strip());
        application.setAppliedAt(request.appliedAt());
        applyAppliedDate(application);
    }

    /** Moving to a post-application status without a date records today as the applied date. */
    private void applyAppliedDate(Application application) {
        boolean applied = application.getStatus() != ApplicationStatus.SAVED;
        if (applied && application.getAppliedAt() == null) {
            application.setAppliedAt(LocalDate.now(ZoneOffset.UTC));
        }
    }

    private void recordChange(Application application, ApplicationStatus before) {
        if (before != application.getStatus()) {
            history.save(new StatusChange(application.getId(), before, application.getStatus()));
            log.info("Application status changed: id={} {} -> {}", application.getId(), before,
                application.getStatus());
        }
    }

    private static Specification<Application> filter(UUID userId, String query, ApplicationStatus status,
                                                     UUID jobId) {
        Specification<Application> spec = (root, q, cb) -> cb.equal(root.get("userId"), userId);
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (jobId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("job").get("id"), jobId));
        }
        if (query != null && !query.isBlank()) {
            String pattern = "%" + escapeLike(query.strip().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("job").get("company")), pattern, '\\'),
                cb.like(cb.lower(root.get("job").get("title")), pattern, '\\')));
        }
        return spec;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
