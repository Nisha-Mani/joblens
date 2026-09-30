package com.joblens.job.dto;

import com.joblens.job.EmploymentType;
import com.joblens.job.Job;
import java.time.Instant;
import java.util.UUID;

public record JobResponse(UUID id, String company, String title, String location,
                          EmploymentType employmentType, String jobDescription, String sourceUrl,
                          Instant createdAt, Instant updatedAt) {

    public static JobResponse from(Job job) {
        return new JobResponse(job.getId(), job.getCompany(), job.getTitle(), job.getLocation(),
            job.getEmploymentType(), job.getJobDescription(), job.getSourceUrl(),
            job.getCreatedAt(), job.getUpdatedAt());
    }
}
