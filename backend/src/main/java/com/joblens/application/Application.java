package com.joblens.application;

import com.joblens.job.Job;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "applications")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, updatable = false)
    private Job job;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(name = "applied_at")
    private LocalDate appliedAt;

    @Column(name = "interview_date")
    private Instant interviewDate;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Application() { }

    public Application(UUID userId, Job job, ApplicationStatus status) {
        this.userId = userId;
        this.job = job;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void setStatus(ApplicationStatus status) { this.status = status; }
    public void setAppliedAt(LocalDate appliedAt) { this.appliedAt = appliedAt; }
    public void setInterviewDate(Instant interviewDate) { this.interviewDate = interviewDate; }
    public void setNotes(String notes) { this.notes = notes; }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Job getJob() { return job; }
    public ApplicationStatus getStatus() { return status; }
    public LocalDate getAppliedAt() { return appliedAt; }
    public Instant getInterviewDate() { return interviewDate; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
