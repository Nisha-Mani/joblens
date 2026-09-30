package com.joblens.interview.dto;

import com.joblens.interview.Difficulty;
import com.joblens.interview.InterviewCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CustomQuestionRequest(
    @NotNull(message = "Job is required") UUID jobId,
    @NotBlank(message = "Question is required")
    @Size(max = 500, message = "Question must be at most 500 characters") String question,
    @NotNull(message = "Category is required") InterviewCategory category,
    @NotNull(message = "Difficulty is required") Difficulty difficulty) { }
