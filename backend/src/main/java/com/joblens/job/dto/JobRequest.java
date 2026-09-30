package com.joblens.job.dto;

import com.joblens.job.EmploymentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record JobRequest(
    @NotBlank(message = "Company is required")
    @Size(max = 150, message = "Company must be at most 150 characters") String company,
    @NotBlank(message = "Title is required")
    @Size(max = 150, message = "Title must be at most 150 characters") String title,
    @Size(max = 150, message = "Location must be at most 150 characters") String location,
    EmploymentType employmentType,
    @NotBlank(message = "Job description is required")
    @Size(max = 20000, message = "Job description must be at most 20000 characters") String jobDescription,
    @Size(max = 2048, message = "Source URL is too long")
    @Pattern(regexp = "^$|^https?://\\S+$", message = "Source URL must start with http:// or https://")
    String sourceUrl) { }
