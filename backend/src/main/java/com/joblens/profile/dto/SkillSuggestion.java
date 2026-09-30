package com.joblens.profile.dto;

import com.joblens.profile.Skill;
import com.joblens.profile.SkillCategory;

public record SkillSuggestion(String name, SkillCategory category) {

    public static SkillSuggestion from(Skill skill) {
        return new SkillSuggestion(skill.getName(), skill.getCategory());
    }
}
