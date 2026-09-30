package com.joblens.job;

import com.joblens.common.error.ApiException;
import com.joblens.common.error.NotFoundException;
import com.joblens.common.web.PageResponse;
import com.joblens.job.dto.JobRequest;
import com.joblens.job.dto.JobResponse;
import com.joblens.job.dto.JobSummary;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    static final int MAX_PAGE_SIZE = 100;
    /** Client-facing sort keys mapped to entity properties; anything else is rejected. */
    private static final List<String> SORTABLE = List.of("createdAt", "company", "title");

    private final JobRepository jobs;

    public JobService(JobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public PageResponse<JobSummary> search(UUID userId, String query, EmploymentType type,
                                           String sortBy, String direction, int page, int size) {
        if (!SORTABLE.contains(sortBy)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot sort by '" + sortBy + "'");
        }
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        // Secondary sort on id keeps pagination stable when the primary key has ties.
        Sort sort = Sort.by(dir, sortBy).and(Sort.by(Sort.Direction.ASC, "id"));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);

        return PageResponse.of(jobs.findAll(filter(userId, query, type), pageable), JobSummary::from);
    }

    @Transactional(readOnly = true)
    public JobResponse get(UUID userId, UUID id) {
        return JobResponse.from(find(userId, id));
    }

    @Transactional
    public JobResponse create(UUID userId, JobRequest request) {
        Job job = new Job(userId);
        apply(job, request);
        Job saved = jobs.save(job);
        log.info("Job created: id={}", saved.getId());
        return JobResponse.from(saved);
    }

    @Transactional
    public JobResponse update(UUID userId, UUID id, JobRequest request) {
        Job job = find(userId, id);
        apply(job, request);
        return JobResponse.from(jobs.save(job));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        jobs.delete(find(userId, id));
        log.info("Job deleted: id={}", id);
    }

    /** For other modules: the job entity if it belongs to the user, otherwise 404. */
    @Transactional(readOnly = true)
    public Job requireOwned(UUID userId, UUID id) {
        return find(userId, id);
    }

    private Job find(UUID userId, UUID id) {
        return jobs.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Job not found"));
    }

    private static void apply(Job job, JobRequest request) {
        job.update(request.company().strip(), request.title().strip(), blankToNull(request.location()),
            request.employmentType() != null ? request.employmentType() : EmploymentType.FULL_TIME,
            request.jobDescription().strip(), blankToNull(request.sourceUrl()));
    }

    private static Specification<Job> filter(UUID userId, String query, EmploymentType type) {
        Specification<Job> spec = (root, q, cb) -> cb.equal(root.get("userId"), userId);
        if (type != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("employmentType"), type));
        }
        if (query != null && !query.isBlank()) {
            String pattern = "%" + escapeLike(query.strip().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("company")), pattern, '\\'),
                cb.like(cb.lower(root.get("title")), pattern, '\\'),
                cb.like(cb.lower(cb.coalesce(root.<String>get("location"), "")), pattern, '\\')));
        }
        return spec;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
