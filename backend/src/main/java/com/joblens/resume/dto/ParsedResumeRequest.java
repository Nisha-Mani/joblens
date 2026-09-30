package com.joblens.resume.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.List;

/** User-corrected parsed data. Limits keep stored documents bounded. */
public record ParsedResumeRequest(
    @Size(max = 120, message = "Name must be at most 120 characters") String name,
    @Email(message = "Enter a valid email address")
    @Size(max = 254, message = "Email is too long") String email,
    @Size(max = 40, message = "Phone must be at most 40 characters") String phone,
    @Size(max = 3000, message = "Summary must be at most 3000 characters") String summary,
    @Size(max = 100, message = "At most 100 skills") List<@Size(max = 40, message = "Skill is too long") String> skills,
    @Size(max = 30, message = "At most 30 experience entries") List<@Size(max = 3000, message = "Entry is too long") String> experience,
    @Size(max = 20, message = "At most 20 education entries") List<@Size(max = 1000, message = "Entry is too long") String> education,
    @Size(max = 30, message = "At most 30 project entries") List<@Size(max = 2000, message = "Entry is too long") String> projects,
    @Size(max = 30, message = "At most 30 certifications") List<@Size(max = 300, message = "Entry is too long") String> certifications) { }
