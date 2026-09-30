package com.joblens.job.dto;

import com.joblens.job.EmploymentType;
import com.joblens.job.Job;
import java.time.Instant;
import java.util.UUID;

/** List view: omits the (potentially large) description. */
public record JobSummary(UUID id, String company, String title, String location,
                         EmploymentType employmentType, Instant createdAt) {

    public static JobSummary from(Job job) {
        return new JobSummary(job.getId(), job.getCompany(), job.getTitle(), job.getLocation(),
            job.getEmploymentType(), job.getCreatedAt());
    }
}
