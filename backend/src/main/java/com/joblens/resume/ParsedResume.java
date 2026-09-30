package com.joblens.resume;

import java.util.List;

/** Structured resume information, produced by a {@link ResumeParser} and editable by the user. */
public record ParsedResume(
    String name,
    String email,
    String phone,
    String summary,
    List<String> skills,
    List<String> experience,
    List<String> education,
    List<String> projects,
    List<String> certifications) {

    public ParsedResume {
        skills = skills == null ? List.of() : List.copyOf(skills);
        experience = experience == null ? List.of() : List.copyOf(experience);
        education = education == null ? List.of() : List.copyOf(education);
        projects = projects == null ? List.of() : List.copyOf(projects);
        certifications = certifications == null ? List.of() : List.copyOf(certifications);
    }
}
