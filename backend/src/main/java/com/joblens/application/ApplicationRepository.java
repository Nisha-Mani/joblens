package com.joblens.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ApplicationRepository
        extends JpaRepository<Application, UUID>, JpaSpecificationExecutor<Application> {

    /** Fetches the job with each page in one query instead of one query per row. */
    @Override
    @EntityGraph(attributePaths = "job")
    Page<Application> findAll(Specification<Application> spec, Pageable pageable);

    @EntityGraph(attributePaths = "job")
    Optional<Application> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByJobId(UUID jobId);
}
