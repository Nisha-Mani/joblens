package com.joblens.application;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusChangeRepository extends JpaRepository<StatusChange, UUID> {

    List<StatusChange> findByApplicationIdOrderByChangedAtAsc(UUID applicationId);
}
