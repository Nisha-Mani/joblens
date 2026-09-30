package com.joblens.resume;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    List<Resume> findByUserIdOrderByVersionDesc(UUID userId);

    Optional<Resume> findByIdAndUserId(UUID id, UUID userId);

    @Query("select coalesce(max(r.version), 0) from Resume r where r.userId = :userId")
    int findMaxVersion(@Param("userId") UUID userId);
}
