package com.joblens.resume;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic parser: regexes for contact details, heading detection for sections and a
 * dictionary of well-known technologies. Fast, free and predictable; the LLM is reserved for
 * semantic analysis rather than basic extraction.
 */
@Component
public class RuleBasedResumeParser implements ResumeParser {

    private enum Section { SUMMARY, SKILLS, EXPERIENCE, EDUCATION, PROJECTS, CERTIFICATIONS }

    private static final Map<String, Section> HEADINGS = Map.ofEntries(
        Map.entry("summary", Section.SUMMARY),
        Map.entry("professional summary", Section.SUMMARY),
        Map.entry("profile", Section.SUMMARY),
        Map.entry("objective", Section.SUMMARY),
        Map.entry("about me", Section.SUMMARY),
        Map.entry("skills", Section.SKILLS),
        Map.entry("technical skills", Section.SKILLS),
        Map.entry("core competencies", Section.SKILLS),
        Map.entry("technologies", Section.SKILLS),
        Map.entry("experience", Section.EXPERIENCE),
        Map.entry("work experience", Section.EXPERIENCE),
        Map.entry("professional experience", Section.EXPERIENCE),
        Map.entry("employment history", Section.EXPERIENCE),
        Map.entry("education", Section.EDUCATION),
        Map.entry("academic background", Section.EDUCATION),
        Map.entry("projects", Section.PROJECTS),
        Map.entry("personal projects", Section.PROJECTS),
        Map.entry("selected projects", Section.PROJECTS),
        Map.entry("certifications", Section.CERTIFICATIONS),
        Map.entry("certificates", Section.CERTIFICATIONS),
        Map.entry("licenses & certifications", Section.CERTIFICATIONS));

    private static final Pattern EMAIL =
        Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE =
        Pattern.compile("(?<![\\w])(\\+?\\d[\\d\\s().-]{7,}\\d)(?![\\w])");
    private static final Pattern NAME_LINE = Pattern.compile("^[\\p{L}][\\p{L}.'-]*( [\\p{L}][\\p{L}.'-]*){1,3}$");
    private static final Pattern BULLET = Pattern.compile("^[•·▪◦*\\-–]\\s*");
    private static final Pattern SKILL_SPLIT = Pattern.compile("[,;|•·▪\\n]");

    private static final int MAX_SKILLS = 100;
    private static final int MAX_SKILL_LENGTH = 40;

    @Override
    public ParsedResume parse(String text) {
        List<String> lines = text.lines().map(String::strip).toList();
        Map<Section, List<String>> sections = splitSections(lines);
        List<String> header = headerLines(lines);

        return new ParsedResume(
            findName(header),
            firstMatch(EMAIL, text),
            findPhone(text),
            summary(sections.get(Section.SUMMARY)),
            extractSkills(text, sections.get(Section.SKILLS)),
            entries(sections.get(Section.EXPERIENCE), true),
            entries(sections.get(Section.EDUCATION), true),
            entries(sections.get(Section.PROJECTS), true),
            entries(sections.get(Section.CERTIFICATIONS), false));
    }

    private static Map<Section, List<String>> splitSections(List<String> lines) {
        Map<Section, List<String>> sections = new LinkedHashMap<>();
        Section current = null;
        for (String line : lines) {
            Section heading = headingOf(line);
            if (heading != null) {
                current = heading;
                sections.putIfAbsent(current, new ArrayList<>());
            } else if (current != null) {
                sections.get(current).add(line);
            }
        }
        return sections;
    }

    private static Section headingOf(String line) {
        if (line.isEmpty() || line.length() > 40) {
            return null;
        }
        String normalized = line.replaceAll("[:\\s]+$", "").toLowerCase(Locale.ROOT);
        return HEADINGS.get(normalized);
    }

    private static List<String> headerLines(List<String> lines) {
        List<String> header = new ArrayList<>();
        for (String line : lines) {
            if (headingOf(line) != null) {
                break;
            }
            if (!line.isEmpty()) {
                header.add(line);
            }
            if (header.size() >= 8) {
                break;
            }
        }
        return header;
    }

    private static String findName(List<String> header) {
        for (String line : header) {
            if (!line.contains("@") && NAME_LINE.matcher(line).matches()) {
                return line;
            }
        }
        return null;
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private static String findPhone(String text) {
        Matcher matcher = PHONE.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1).strip();
            long digits = candidate.chars().filter(Character::isDigit).count();
            if (digits >= 10 && digits <= 15) {
                return candidate;
            }
        }
        return null;
    }

    private static String summary(List<String> lines) {
        if (lines == null) {
            return null;
        }
        String joined = String.join(" ", lines.stream().filter(l -> !l.isEmpty()).toList()).strip();
        return joined.isEmpty() ? null : joined;
    }

    private static List<String> extractSkills(String fullText, List<String> skillsSection) {
        Set<String> skills = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        boolean hasSection = skillsSection != null;

        if (hasSection) {
            for (String line : skillsSection) {
                String content = BULLET.matcher(line).replaceFirst("");
                int colon = content.indexOf(':');
                if (colon >= 0) {
                    content = content.substring(colon + 1);
                }
                for (String token : SKILL_SPLIT.split(content)) {
                    addSkill(skills, seen, token.strip());
                }
            }
        }
        for (String tech : TechnologyDictionary.TERMS) {
            boolean inSection = hasSection && TechnologyDictionary.containsTerm(String.join("\n", skillsSection), tech);
            boolean ambiguous = TechnologyDictionary.AMBIGUOUS.contains(tech);
            boolean coveredByLongerSkill = skills.stream()
                .anyMatch(s -> s.toLowerCase(Locale.ROOT).startsWith(tech.toLowerCase(Locale.ROOT) + " "));
            if (TechnologyDictionary.containsTerm(fullText, tech) && (!ambiguous || inSection) && !coveredByLongerSkill) {
                addSkill(skills, seen, tech);
            }
        }
        return skills.stream().limit(MAX_SKILLS).toList();
    }

    private static void addSkill(Set<String> skills, Set<String> seen, String candidate) {
        if (candidate.isEmpty() || candidate.length() > MAX_SKILL_LENGTH) {
            return;
        }
        if (seen.add(candidate.toLowerCase(Locale.ROOT))) {
            skills.add(candidate);
        }
    }

    /** Groups section lines into entries; blank lines separate entries when {@code byBlankLine}. */
    private static List<String> entries(List<String> lines, boolean byBlankLine) {
        if (lines == null) {
            return List.of();
        }
        List<String> entries = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (line.isEmpty()) {
                if (byBlankLine) {
                    flush(entries, current);
                }
                continue;
            }
            if (byBlankLine) {
                if (!current.isEmpty()) {
                    current.append('\n');
                }
                current.append(line);
            } else {
                entries.add(BULLET.matcher(line).replaceFirst(""));
            }
        }
        flush(entries, current);
        return entries;
    }

    private static void flush(List<String> entries, StringBuilder current) {
        if (!current.isEmpty()) {
            entries.add(current.toString());
            current.setLength(0);
        }
    }
}
