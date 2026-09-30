package com.joblens.profile;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSkillRepository extends JpaRepository<UserSkill, UserSkillId> {

    List<UserSkill> findByIdUserIdOrderBySkillNameAsc(UUID userId);
}
