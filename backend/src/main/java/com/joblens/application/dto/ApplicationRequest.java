package com.joblens.application.dto;

import com.joblens.application.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Used for both create (jobId required) and update (jobId ignored). */
public record ApplicationRequest(
    UUID jobId,
    @NotNull(message = "Status is required") ApplicationStatus status,
    LocalDate appliedAt,
    Instant interviewDate,
    @Size(max = 10000, message = "Notes must be at most 10000 characters") String notes) { }
