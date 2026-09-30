package com.joblens.profile.dto;

import com.joblens.profile.Proficiency;
import com.joblens.profile.SkillCategory;
import com.joblens.profile.UserSkill;
import java.util.UUID;

public record UserSkillResponse(
    UUID skillId, String name, SkillCategory category, Proficiency proficiency) {

    public static UserSkillResponse from(UserSkill userSkill) {
        return new UserSkillResponse(userSkill.getSkill().getId(), userSkill.getSkill().getName(),
            userSkill.getSkill().getCategory(), userSkill.getProficiency());
    }
}
