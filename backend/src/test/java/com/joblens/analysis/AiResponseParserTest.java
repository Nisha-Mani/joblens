package com.joblens.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joblens.analysis.ai.AiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class AiResponseParserTest {

    private final AiResponseParser parser = new AiResponseParser(JsonMapper.builder().build());

    private static String valid() {
        return """
            {"overallScore":82,"matchingSkills":["Java","React"],"missingSkills":["Kubernetes"],
             "keywordGaps":["observability"],"experienceAssessment":"Solid backend experience.",
             "suggestions":["Quantify impact"],"interviewTopics":["System design"]}""";
    }

    @Test
    void parsesValidResponse() {
        AnalysisResult result = parser.parse(valid());
        assertThat(result.overallScore()).isEqualTo(82);
        assertThat(result.matchingSkills()).containsExactly("Java", "React");
        assertThat(result.missingSkills()).containsExactly("Kubernetes");
        assertThat(result.experienceAssessment()).isEqualTo("Solid backend experience.");
    }

    @Test
    void acceptsMarkdownFencedJson() {
        assertThat(parser.parse("```json\n" + valid() + "\n```").overallScore()).isEqualTo(82);
    }

    @Test
    void acceptsJsonSurroundedByChatter() {
        assertThat(parser.parse("Here you go:\n" + valid() + "\nHope that helps!").overallScore()).isEqualTo(82);
    }

    @Test
    void acceptsIntegerValuedDecimalScore() {
        assertThat(parser.parse(valid().replace("82", "82.0")).overallScore()).isEqualTo(82);
    }

    @Test
    void ignoresUnknownExtraFields() {
        assertThat(parser.parse(valid().replace("{\"overallScore\"", "{\"extra\":true,\"overallScore\"")).overallScore())
            .isEqualTo(82);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not json at all", "[1,2,3]", "\"just a string\"", "{", "null"})
    void rejectsNonObjectOutput(String raw) {
        assertThatThrownBy(() -> parser.parse(raw)).isInstanceOf(AiException.class);
    }

    @Test
    void rejectsNullOutput() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(AiException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "101", "1000", "82.5", "\"82\"", "null", "true"})
    void rejectsBadScores(String score) {
        assertThatThrownBy(() -> parser.parse(valid().replace("82", score))).isInstanceOf(AiException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"matchingSkills", "missingSkills", "keywordGaps", "suggestions", "interviewTopics", "experienceAssessment", "overallScore"})
    void rejectsMissingRequiredFields(String field) {
        String without = valid().replaceFirst("\"" + field + "\":", "\"ignored\":");
        assertThatThrownBy(() -> parser.parse(without)).isInstanceOf(AiException.class);
    }

    @Test
    void rejectsWrongTypesInLists() {
        assertThatThrownBy(() -> parser.parse(valid().replace("[\"Java\",\"React\"]", "[\"Java\", 5]")))
            .isInstanceOf(AiException.class);
        assertThatThrownBy(() -> parser.parse(valid().replace("[\"Java\",\"React\"]", "\"Java, React\"")))
            .isInstanceOf(AiException.class);
    }

    @Test
    void rejectsBlankAssessment() {
        assertThatThrownBy(() -> parser.parse(valid().replace("Solid backend experience.", "  ")))
            .isInstanceOf(AiException.class);
    }

    @Test
    void rejectsSkillListedAsBothMatchingAndMissing() {
        assertThatThrownBy(() -> parser.parse(valid().replace("[\"Kubernetes\"]", "[\"java\"]")))
            .isInstanceOf(AiException.class);
    }

    @Test
    void trimsDedupesAndDropsBlankItems() {
        AnalysisResult result = parser.parse(valid().replace("[\"Java\",\"React\"]", "[\" Java \",\"JAVA\",\"\",\"  \",\"React\"]"));
        assertThat(result.matchingSkills()).containsExactly("Java", "React");
    }

    @Test
    void boundsOversizedStringsAndLists() {
        String longText = "x".repeat(5000);
        String manySkills = "[" + String.join(",", java.util.stream.IntStream.range(0, 200).mapToObj(i -> "\"skill" + i + "\"").toList()) + "]";
        AnalysisResult result = parser.parse(valid()
            .replace("Solid backend experience.", longText)
            .replace("[\"Kubernetes\"]", manySkills));
        assertThat(result.experienceAssessment()).hasSizeLessThanOrEqualTo(AiResponseParser.MAX_ASSESSMENT_LENGTH);
        assertThat(result.missingSkills()).hasSize(AiResponseParser.MAX_SKILLS);
    }

    @Test
    void allowsEmptyListsForAPerfectMatch() {
        AnalysisResult result = parser.parse("""
            {"overallScore":100,"matchingSkills":["Java"],"missingSkills":[],"keywordGaps":[],
             "experienceAssessment":"Excellent fit.","suggestions":[],"interviewTopics":[]}""");
        assertThat(result.missingSkills()).isEmpty();
    }

    @Test
    void errorMessageNeverEchoesModelOutput() {
        assertThatThrownBy(() -> parser.parse("SECRET-RESUME-CONTENT not json"))
            .isInstanceOf(AiException.class)
            .satisfies(e -> assertThat(e.getMessage()).doesNotContain("SECRET-RESUME-CONTENT"));
    }
}
