package com.joblens.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false, unique = true)
    private UUID userId;

    private String name;
    private String headline;
    private String location;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfile() { }

    public UserProfile(UUID userId) {
        this.userId = userId;
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

    public void update(String name, String headline, String location,
                       Integer yearsOfExperience, String summary) {
        this.name = name;
        this.headline = headline;
        this.location = location;
        this.yearsOfExperience = yearsOfExperience;
        this.summary = summary;
    }

    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public String getHeadline() { return headline; }
    public String getLocation() { return location; }
    public Integer getYearsOfExperience() { return yearsOfExperience; }
    public String getSummary() { return summary; }
}
