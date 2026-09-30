package com.joblens.analysis;

import java.util.List;

/** Validated, normalised output of a resume/job analysis. Only created by {@link AiResponseParser}. */
public record AnalysisResult(int overallScore, List<String> matchingSkills, List<String> missingSkills,
                             List<String> keywordGaps, String experienceAssessment,
                             List<String> suggestions, List<String> interviewTopics) { }
