package com.joblens.profile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ProfileRequest(
    @Size(max = 120, message = "Name must be at most 120 characters") String name,
    @Size(max = 160, message = "Headline must be at most 160 characters") String headline,
    @Size(max = 120, message = "Location must be at most 120 characters") String location,
    @Min(value = 0, message = "Years of experience cannot be negative")
    @Max(value = 60, message = "Years of experience must be 60 or fewer") Integer yearsOfExperience,
    @Size(max = 2000, message = "Summary must be at most 2000 characters") String summary) { }
