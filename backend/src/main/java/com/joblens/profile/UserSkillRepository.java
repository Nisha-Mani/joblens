package com.joblens.profile;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSkillRepository extends JpaRepository<UserSkill, UserSkillId> {

    /** Adds the skill for the user, or updates its proficiency if they already have it; race-safe. */
    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO user_skills (user_id, skill_id, proficiency) VALUES (:userId, :skillId, :proficiency) "
        + "ON CONFLICT (user_id, skill_id) DO UPDATE SET proficiency = EXCLUDED.proficiency", nativeQuery = true)
    void upsert(@Param("userId") UUID userId, @Param("skillId") UUID skillId, @Param("proficiency") String proficiency);

    List<UserSkill> findByIdUserIdOrderBySkillNameAsc(UUID userId);
}
