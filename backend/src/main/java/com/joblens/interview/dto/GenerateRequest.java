package com.joblens.interview.dto;

import java.util.UUID;

/** Optional: which resume version to tailor questions to. Defaults to the newest, or none. */
public record GenerateRequest(UUID resumeId) { }
