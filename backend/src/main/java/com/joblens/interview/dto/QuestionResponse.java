package com.joblens.interview.dto;

import com.joblens.interview.Difficulty;
import com.joblens.interview.InterviewCategory;
import com.joblens.interview.PrepStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuestionResponse(UUID id, UUID jobId, String company, String jobTitle, String question,
                               InterviewCategory category, Difficulty difficulty, List<String> skills,
                               String notes, PrepStatus status, boolean generated,
                               Instant createdAt, Instant updatedAt) { }
