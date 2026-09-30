package com.joblens.interview;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InterviewQuestionRepository
        extends JpaRepository<InterviewQuestion, UUID>, JpaSpecificationExecutor<InterviewQuestion> {

    @Override
    @EntityGraph(attributePaths = "job")
    Page<InterviewQuestion> findAll(Specification<InterviewQuestion> spec, Pageable pageable);

    @EntityGraph(attributePaths = "job")
    Optional<InterviewQuestion> findByIdAndUserId(UUID id, UUID userId);

    List<InterviewQuestion> findByJobIdAndUserId(UUID jobId, UUID userId);
}
