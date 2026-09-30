package com.joblens.profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    /**
     * Creates the skill unless one with the same name (case-insensitive) already exists. Letting the
     * database arbitrate is safe under concurrency; catching a constraint violation is not, because
     * PostgreSQL aborts the whole transaction after one.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO skills (id, name, category) VALUES (gen_random_uuid(), :name, :category) "
        + "ON CONFLICT ((lower(name))) DO NOTHING", nativeQuery = true)
    void insertIfAbsent(@Param("name") String name, @Param("category") String category);

    Optional<Skill> findByNameIgnoreCase(String name);

    @Query("select s from Skill s where lower(s.name) like lower(concat(:prefix, '%')) order by s.name")
    List<Skill> searchByPrefix(@Param("prefix") String prefix, Pageable pageable);
}
