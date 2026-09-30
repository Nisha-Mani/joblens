package com.joblens.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserSkillId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "skill_id")
    private UUID skillId;

    protected UserSkillId() { }

    public UserSkillId(UUID userId, UUID skillId) {
        this.userId = userId;
        this.skillId = skillId;
    }

    public UUID getUserId() { return userId; }
    public UUID getSkillId() { return skillId; }

    @Override
    public boolean equals(Object o) {
        return o instanceof UserSkillId other
            && Objects.equals(userId, other.userId) && Objects.equals(skillId, other.skillId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, skillId);
    }
}
