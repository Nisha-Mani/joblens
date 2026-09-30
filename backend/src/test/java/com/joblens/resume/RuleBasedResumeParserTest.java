package com.joblens.resume;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuleBasedResumeParserTest {

    private final RuleBasedResumeParser parser = new RuleBasedResumeParser();

    private static final String RESUME = """
        Jane Developer
        jane.dev@example.com | +1 (415) 555-0134

        SUMMARY
        Full-stack engineer with 6 years of experience.
        Loves clean APIs.

        SKILLS
        Languages: Java, TypeScript, Python
        • Spring Boot | React | Docker

        EXPERIENCE
        Senior Engineer, Acme Corp (2021 - Present)
        Built REST APIs with Spring Boot and PostgreSQL.

        Engineer, Beta LLC (2018 - 2021)
        Worked on React dashboards.

        EDUCATION
        B.S. Computer Science, State University (2018)

        PROJECTS
        JobLens - job tracker built with React and Java

        CERTIFICATIONS
        • AWS Certified Developer
        • Oracle Certified Professional
        """;

    @Test
    void extractsContactDetails() {
        ParsedResume parsed = parser.parse(RESUME);
        assertThat(parsed.name()).isEqualTo("Jane Developer");
        assertThat(parsed.email()).isEqualTo("jane.dev@example.com");
        assertThat(parsed.phone()).isEqualTo("+1 (415) 555-0134");
    }

    @Test
    void extractsSummaryAcrossLines() {
        assertThat(parser.parse(RESUME).summary())
            .isEqualTo("Full-stack engineer with 6 years of experience. Loves clean APIs.");
    }

    @Test
    void extractsSkillsFromSectionAndKnownTechnologiesWithoutDuplicates() {
        assertThat(parser.parse(RESUME).skills())
            .contains("Java", "TypeScript", "Python", "Spring Boot", "React", "Docker", "PostgreSQL")
            .doesNotHaveDuplicates();
    }

    @Test
    void doesNotListShortTermWhenLongerSkillCoversIt() {
        assertThat(parser.parse("Some Person\nSKILLS\nSpring Boot, React\n").skills())
            .contains("Spring Boot").doesNotContain("Spring");
    }

    @Test
    void groupsExperienceEntriesByBlankLine() {
        ParsedResume parsed = parser.parse(RESUME);
        assertThat(parsed.experience()).hasSize(2);
        assertThat(parsed.experience().get(0)).startsWith("Senior Engineer, Acme Corp");
        assertThat(parsed.experience().get(1)).startsWith("Engineer, Beta LLC");
    }

    @Test
    void extractsEducationProjectsAndCertifications() {
        ParsedResume parsed = parser.parse(RESUME);
        assertThat(parsed.education()).containsExactly("B.S. Computer Science, State University (2018)");
        assertThat(parsed.projects()).hasSize(1);
        assertThat(parsed.certifications())
            .containsExactly("AWS Certified Developer", "Oracle Certified Professional");
    }

    @Test
    void headingsAreCaseInsensitiveAndAllowColons() {
        ParsedResume parsed = parser.parse("Some Person\nskills:\nJava, Go\n");
        assertThat(parsed.skills()).contains("Java", "Go");
    }

    @Test
    void ambiguousTermsAreIgnoredOutsideSkillsSection() {
        ParsedResume parsed = parser.parse("Some Person\n\nEXPERIENCE\nWill go to the office and swift delivery\n");
        assertThat(parsed.skills()).doesNotContain("Go", "Swift");
    }

    @Test
    void handlesUnstructuredTextWithoutThrowing() {
        ParsedResume parsed = parser.parse("just a few words with no structure at all");
        assertThat(parsed.name()).isNull();
        assertThat(parsed.email()).isNull();
        assertThat(parsed.skills()).isEmpty();
        assertThat(parsed.experience()).isEmpty();
    }

    @Test
    void shortNumbersAreNotPhones() {
        assertThat(parser.parse("Name Here\nZip 12345, year 2021-2023").phone()).isNull();
    }
}
