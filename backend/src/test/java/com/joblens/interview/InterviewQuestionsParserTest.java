package com.joblens.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joblens.analysis.ai.AiException;
import com.joblens.interview.InterviewQuestionsParser.GeneratedQuestion;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class InterviewQuestionsParserTest {

    private final InterviewQuestionsParser parser = new InterviewQuestionsParser(JsonMapper.builder().build());

    private static String item(String question, String category, String difficulty, String skills) {
        return "{\"question\":\"%s\",\"category\":\"%s\",\"difficulty\":\"%s\",\"skills\":%s}"
            .formatted(question, category, difficulty, skills);
    }

    private static String wrap(String... items) {
        return "{\"questions\":[" + String.join(",", items) + "]}";
    }

    @Test
    void parsesValidQuestions() {
        List<GeneratedQuestion> result = parser.parse(wrap(
            item("Explain Kubernetes pods", "TECHNICAL", "HARD", "[\"Kubernetes\"]"),
            item("Tell me about a conflict", "BEHAVIORAL", "EASY", "[]")));
        assertThat(result).hasSize(2);
        assertThat(result.get(0).category()).isEqualTo(InterviewCategory.TECHNICAL);
        assertThat(result.get(0).difficulty()).isEqualTo(Difficulty.HARD);
        assertThat(result.get(0).skills()).containsExactly("Kubernetes");
    }

    @Test
    void toleratesFencesAndCaseAndSeparatorVariantsInEnums() {
        String raw = "```json\n" + wrap(item("Why this role?", "role-specific", "medium", "[]")) + "\n```";
        GeneratedQuestion q = parser.parse(raw).get(0);
        assertThat(q.category()).isEqualTo(InterviewCategory.ROLE_SPECIFIC);
        assertThat(q.difficulty()).isEqualTo(Difficulty.MEDIUM);
    }

    @Test
    void collapsesDuplicateQuestionsIgnoringCaseAndWhitespace() {
        List<GeneratedQuestion> result = parser.parse(wrap(
            item("What is   a pod?", "TECHNICAL", "EASY", "[]"),
            item("what is a POD?", "TECHNICAL", "HARD", "[]")));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).difficulty()).isEqualTo(Difficulty.EASY);
    }

    @Test
    void boundsListSizeQuestionLengthAndSkills() {
        String many = java.util.stream.IntStream.range(0, 40)
            .mapToObj(i -> item("Question number " + i, "TECHNICAL", "EASY", "[]")).toList().toString();
        List<GeneratedQuestion> result = parser.parse("{\"questions\":" + many + "}");
        assertThat(result).hasSize(InterviewQuestionsParser.MAX_QUESTIONS);

        GeneratedQuestion long1 = parser.parse(wrap(item("q".repeat(900), "TECHNICAL", "EASY",
            "[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\"]"))).get(0);
        assertThat(long1.question()).hasSizeLessThanOrEqualTo(InterviewQuestionsParser.MAX_QUESTION_LENGTH);
        assertThat(long1.skills()).hasSize(InterviewQuestionsParser.MAX_SKILLS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "nope", "[]", "{\"questions\":[]}", "{\"questions\":\"none\"}", "{}", "{\"questions\":[1]}"})
    void rejectsUnusableResponses(String raw) {
        assertThatThrownBy(() -> parser.parse(raw)).isInstanceOf(AiException.class);
    }

    @Test
    void oneBadItemInvalidatesTheWholeResponse() {
        assertThatThrownBy(() -> parser.parse(wrap(
            item("Good question", "TECHNICAL", "EASY", "[]"),
            item("Bad category", "TRICKY", "EASY", "[]")))).isInstanceOf(AiException.class);
        assertThatThrownBy(() -> parser.parse(wrap(item("Bad difficulty", "TECHNICAL", "IMPOSSIBLE", "[]"))))
            .isInstanceOf(AiException.class);
        assertThatThrownBy(() -> parser.parse(wrap(item("   ", "TECHNICAL", "EASY", "[]"))))
            .isInstanceOf(AiException.class);
        assertThatThrownBy(() -> parser.parse(wrap(item("Skills wrong type", "TECHNICAL", "EASY", "\"Java\""))))
            .isInstanceOf(AiException.class);
        assertThatThrownBy(() -> parser.parse(wrap(item("Skills non-string", "TECHNICAL", "EASY", "[1]"))))
            .isInstanceOf(AiException.class);
    }
}
