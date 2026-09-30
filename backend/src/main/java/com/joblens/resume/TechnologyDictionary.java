package com.joblens.resume;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Well-known technology names, shared by resume parsing and job-description analysis. */
public final class TechnologyDictionary {

    public static final List<String> TERMS = List.of(
        "Java", "Kotlin", "Scala", "Python", "JavaScript", "TypeScript", "Node.js", "React", "Angular",
        "Vue", "Next.js", "Spring Boot", "Spring", "Hibernate", "JUnit", "Mockito", "Jest", "Playwright",
        "Cypress", "Selenium", "HTML", "CSS", "Tailwind CSS", "Redux", "GraphQL", "REST", "SQL",
        "PostgreSQL", "MySQL", "MongoDB", "DynamoDB", "Redis", "Kafka", "RabbitMQ", "Elasticsearch",
        "AWS", "Azure", "GCP", "Docker", "Kubernetes", "Terraform", "Jenkins", "GitHub Actions", "CI/CD",
        "Git", "Linux", "Maven", "Gradle", "Webpack", "Vite", "Express", "Django", "Flask", "FastAPI",
        "OpenAI", "LangChain", "C#", ".NET", "C++", "Go", "Rust", "PHP", "Ruby", "Swift");

    /** Terms that are also ordinary words, so they only count when a skills section lists them. */
    public static final Set<String> AMBIGUOUS = Set.of("Go", "Rust", "Swift", "Spring", "REST");

    private TechnologyDictionary() { }

    public static boolean containsTerm(String text, String term) {
        Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])");
        return pattern.matcher(text).find();
    }

    /** Unambiguous technologies mentioned in free text, longest-match aware ("Spring Boot" hides "Spring"). */
    public static List<String> detect(String text) {
        List<String> found = new ArrayList<>();
        for (String term : TERMS) {
            if (AMBIGUOUS.contains(term) || !containsTerm(text, term)) {
                continue;
            }
            boolean covered = found.stream().anyMatch(f -> f.toLowerCase().startsWith(term.toLowerCase() + " "));
            if (!covered) {
                found.add(term);
            }
        }
        return found;
    }
}
