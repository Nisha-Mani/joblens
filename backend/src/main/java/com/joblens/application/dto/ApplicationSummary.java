package com.joblens.application.dto;

import com.joblens.application.Application;
import com.joblens.application.ApplicationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ApplicationSummary(UUID id, UUID jobId, String company, String title,
                                 ApplicationStatus status, LocalDate appliedAt,
                                 Instant interviewDate, Instant updatedAt) {

    public static ApplicationSummary from(Application a) {
        return new ApplicationSummary(a.getId(), a.getJob().getId(), a.getJob().getCompany(),
            a.getJob().getTitle(), a.getStatus(), a.getAppliedAt(), a.getInterviewDate(), a.getUpdatedAt());
    }
}
