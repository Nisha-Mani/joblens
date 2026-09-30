package com.joblens.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysis, UUID> {

    List<ResumeAnalysis> findByJobIdAndUserIdOrderByCreatedAtDesc(UUID jobId, UUID userId);

    Optional<ResumeAnalysis> findByIdAndUserId(UUID id, UUID userId);
}
