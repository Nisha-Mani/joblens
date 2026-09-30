package com.joblens.analysis.ai;

/** Raw model output plus the model that produced it. Never trusted until validated. */
public record AiResponse(String content, String model) { }
