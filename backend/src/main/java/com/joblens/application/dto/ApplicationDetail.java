package com.joblens.application.dto;

import com.joblens.application.Application;
import com.joblens.application.ApplicationStatus;
import com.joblens.application.StatusChange;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ApplicationDetail(UUID id, UUID jobId, String company, String title, String location,
                                ApplicationStatus status, LocalDate appliedAt, Instant interviewDate,
                                String notes, Instant createdAt, Instant updatedAt,
                                List<HistoryEntry> history) {

    public record HistoryEntry(ApplicationStatus from, ApplicationStatus to, Instant changedAt) { }

    public static ApplicationDetail from(Application a, List<StatusChange> changes) {
        return new ApplicationDetail(a.getId(), a.getJob().getId(), a.getJob().getCompany(),
            a.getJob().getTitle(), a.getJob().getLocation(), a.getStatus(), a.getAppliedAt(),
            a.getInterviewDate(), a.getNotes(), a.getCreatedAt(), a.getUpdatedAt(),
            changes.stream().map(c -> new HistoryEntry(c.getFromStatus(), c.getToStatus(), c.getChangedAt())).toList());
    }
}
