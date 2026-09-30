package com.joblens.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "resume_analyses")
public class ResumeAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "resume_id")
    private UUID resumeId;

    @Column(name = "resume_version", nullable = false)
    private int resumeVersion;

    @Column(name = "overall_score", nullable = false)
    private int overallScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matching_skills", nullable = false, columnDefinition = "jsonb")
    private String matchingSkills;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_skills", nullable = false, columnDefinition = "jsonb")
    private String missingSkills;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keyword_gaps", nullable = false, columnDefinition = "jsonb")
    private String keywordGaps;

    @Column(name = "experience_assessment", nullable = false, columnDefinition = "text")
    private String experienceAssessment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String suggestions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interview_topics", nullable = false, columnDefinition = "jsonb")
    private String interviewTopics;

    @Column(nullable = false)
    private String model;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ResumeAnalysis() { }

    public ResumeAnalysis(UUID userId, UUID jobId, UUID resumeId, int resumeVersion, int overallScore,
                          String matchingSkills, String missingSkills, String keywordGaps,
                          String experienceAssessment, String suggestions, String interviewTopics,
                          String model) {
        this.userId = userId;
        this.jobId = jobId;
        this.resumeId = resumeId;
        this.resumeVersion = resumeVersion;
        this.overallScore = overallScore;
        this.matchingSkills = matchingSkills;
        this.missingSkills = missingSkills;
        this.keywordGaps = keywordGaps;
        this.experienceAssessment = experienceAssessment;
        this.suggestions = suggestions;
        this.interviewTopics = interviewTopics;
        this.model = model;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getJobId() { return jobId; }
    public UUID getResumeId() { return resumeId; }
    public int getResumeVersion() { return resumeVersion; }
    public int getOverallScore() { return overallScore; }
    public String getMatchingSkills() { return matchingSkills; }
    public String getMissingSkills() { return missingSkills; }
    public String getKeywordGaps() { return keywordGaps; }
    public String getExperienceAssessment() { return experienceAssessment; }
    public String getSuggestions() { return suggestions; }
    public String getInterviewTopics() { return interviewTopics; }
    public String getModel() { return model; }
    public Instant getCreatedAt() { return createdAt; }
}
