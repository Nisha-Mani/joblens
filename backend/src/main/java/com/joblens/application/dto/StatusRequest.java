package com.joblens.application.dto;

import com.joblens.application.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull(message = "Status is required") ApplicationStatus status) { }
