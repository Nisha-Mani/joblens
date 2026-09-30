package com.joblens.interview.dto;

import com.joblens.interview.PrepStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PrepUpdateRequest(
    @Size(max = 5000, message = "Notes must be at most 5000 characters") String notes,
    @NotNull(message = "Status is required") PrepStatus status) { }
