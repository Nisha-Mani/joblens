package com.joblens.analysis.dto;

import java.util.UUID;

/** Optional: which resume version to use. Defaults to the newest one. */
public record AnalyzeRequest(UUID resumeId) { }
