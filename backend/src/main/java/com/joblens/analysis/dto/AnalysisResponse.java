package com.joblens.analysis.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisResponse(UUID id, UUID jobId, UUID resumeId, int resumeVersion, int overallScore,
                               List<String> matchingSkills, List<String> missingSkills,
                               List<String> keywordGaps, String experienceAssessment,
                               List<String> suggestions, List<String> interviewTopics,
                               String model, Instant createdAt) { }
