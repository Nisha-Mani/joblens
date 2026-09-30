package com.joblens.analysis.dto;

import java.time.Instant;
import java.util.UUID;

public record AnalysisSummary(UUID id, int resumeVersion, int overallScore, String model, Instant createdAt) { }
