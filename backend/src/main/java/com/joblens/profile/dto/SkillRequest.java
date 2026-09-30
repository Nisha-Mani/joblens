package com.joblens.profile.dto;

import com.joblens.profile.Proficiency;
import com.joblens.profile.SkillCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SkillRequest(
    @NotBlank(message = "Skill name is required")
    @Size(max = 60, message = "Skill name must be at most 60 characters") String name,
    SkillCategory category,
    @NotNull(message = "Proficiency is required") Proficiency proficiency) { }
