package com.joblens.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "skills")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SkillCategory category = SkillCategory.OTHER;

    protected Skill() { }

    public Skill(String name, SkillCategory category) {
        this.name = name;
        this.category = category;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public SkillCategory getCategory() { return category; }
}
