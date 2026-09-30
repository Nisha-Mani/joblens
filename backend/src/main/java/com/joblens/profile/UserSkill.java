package com.joblens.profile;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_skills")
public class UserSkill {

    @EmbeddedId
    private UserSkillId id;

    @MapsId("skillId")
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Proficiency proficiency;

    protected UserSkill() { }

    public UserSkill(java.util.UUID userId, Skill skill, Proficiency proficiency) {
        this.id = new UserSkillId(userId, skill.getId());
        this.skill = skill;
        this.proficiency = proficiency;
    }

    public UserSkillId getId() { return id; }
    public Skill getSkill() { return skill; }
    public Proficiency getProficiency() { return proficiency; }
    public void setProficiency(Proficiency proficiency) { this.proficiency = proficiency; }
}
