package com.joblens.profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    Optional<Skill> findByNameIgnoreCase(String name);

    @Query("select s from Skill s where lower(s.name) like lower(concat(:prefix, '%')) order by s.name")
    List<Skill> searchByPrefix(@Param("prefix") String prefix, Pageable pageable);
}
