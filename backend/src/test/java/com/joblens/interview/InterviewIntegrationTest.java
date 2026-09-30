package com.joblens.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joblens.support.TestAuth;
import com.joblens.support.TestPdf;
import com.joblens.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewIntegrationTest {

    /** 5 technical (one per detected skill, capped) + 3 behavioral + 2 project + 2 role-specific. */
    private static final int GENERATED = 12;

    private static final String DESCRIPTION = "5+ years. Java, Spring Boot, Kubernetes, Docker, PostgreSQL and AWS.";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired InterviewQuestionRepository questions;

    private String alice;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        alice = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "alice@example.com");
        bob = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "bob@example.com");
    }

    private String createJob(String bearer, String title) throws Exception {
        String body = mockMvc.perform(post("/api/jobs").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                    "company", "Globex", "title", title, "jobDescription", DESCRIPTION))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void uploadResume(String bearer) throws Exception {
        mockMvc.perform(multipart("/api/resumes")
            .file(new MockMultipartFile("file", "jane.pdf", "application/pdf", TestPdf.fromLines(TestPdf.SAMPLE_RESUME)))
            .header("Authorization", bearer)).andExpect(status().isCreated());
    }

    private ResultActions generate(String bearer, String jobId) throws Exception {
        return mockMvc.perform(post("/api/jobs/" + jobId + "/interview-questions/generate").header("Authorization", bearer));
    }

    private ResultActions list(String bearer, String... params) throws Exception {
        var request = get("/api/interviews/questions").header("Authorization", bearer);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private String firstQuestionId(String bearer, String... params) throws Exception {
        return objectMapper.readTree(list(bearer, params).andReturn().getResponse().getContentAsString())
            .get("content").get(0).get("id").asText();
    }

    @Test
    void generatesQuestionsAcrossAllCategoriesWithDefaults() throws Exception {
        uploadResume(alice);
        String jobId = createJob(alice, "Platform Engineer");

        generate(alice, jobId)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.length()").value(GENERATED))
            .andExpect(jsonPath("$[0].status").value("NOT_STARTED"))
            .andExpect(jsonPath("$[0].generated").value(true))
            .andExpect(jsonPath("$[0].company").value("Globex"));

        for (String category : new String[] {"TECHNICAL", "BEHAVIORAL", "PROJECT", "ROLE_SPECIFIC"}) {
            list(alice, "category", category).andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));
        }
        // Technical questions carry the job's skills.
        list(alice, "category", "TECHNICAL")
            .andExpect(jsonPath("$.content[?(@.skills.length() > 0)]").isNotEmpty());
    }

    @Test
    void worksWithoutAResume() throws Exception {
        String jobId = createJob(alice, "Platform Engineer");
        generate(alice, jobId).andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(GENERATED));
    }

    @Test
    void regeneratingDoesNotCreateDuplicates() throws Exception {
        String jobId = createJob(alice, "Platform Engineer");
        generate(alice, jobId).andExpect(jsonPath("$.length()").value(GENERATED));
        generate(alice, jobId).andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(0));
        list(alice, "jobId", jobId).andExpect(jsonPath("$.totalElements").value(GENERATED));
    }

    @Test
    void notesAndPreparationStatusAreSavedAndValidated() throws Exception {
        String jobId = createJob(alice, "Platform Engineer");
        generate(alice, jobId);
        String id = firstQuestionId(alice);

        mockMvc.perform(put("/api/interviews/questions/" + id).header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"  Use the STAR format  \",\"status\":\"IN_PROGRESS\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes").value("Use the STAR format"))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        list(alice, "status", "IN_PROGRESS").andExpect(jsonPath("$.totalElements").value(1));
        list(alice, "status", "NOT_STARTED").andExpect(jsonPath("$.totalElements").value(GENERATED - 1));

        mockMvc.perform(put("/api/interviews/questions/" + id).header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"" + "x".repeat(5001) + "\",\"status\":\"PREPARED\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.notes").value("Notes must be at most 5000 characters"));
        mockMvc.perform(put("/api/interviews/questions/" + id).header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"a\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.status").value("Status is required"));
    }

    @Test
    void userCanAddAndDeleteTheirOwnQuestion() throws Exception {
        String jobId = createJob(alice, "Platform Engineer");
        String body = mockMvc.perform(post("/api/interviews/questions").header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\":\"%s\",\"question\":\"  Why Globex?  \",\"category\":\"BEHAVIORAL\",\"difficulty\":\"EASY\"}".formatted(jobId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.question").value("Why Globex?"))
            .andExpect(jsonPath("$.generated").value(false))
            .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(delete("/api/interviews/questions/" + id).header("Authorization", alice)).andExpect(status().isNoContent());
        list(alice).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void customQuestionValidationAndOwnership() throws Exception {
        String alicesJob = createJob(alice, "Platform Engineer");
        mockMvc.perform(post("/api/interviews/questions").header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":\"%s\",\"question\":\"\"}".formatted(alicesJob)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.question").value("Question is required"))
            .andExpect(jsonPath("$.errors.category").value("Category is required"));
        mockMvc.perform(post("/api/interviews/questions").header("Authorization", bob)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\":\"%s\",\"question\":\"Q\",\"category\":\"TECHNICAL\",\"difficulty\":\"EASY\"}".formatted(alicesJob)))
            .andExpect(status().isNotFound());
    }

    @Test
    void filtersByJobAndPaginates() throws Exception {
        String jobA = createJob(alice, "Role A");
        String jobB = createJob(alice, "Role B");
        generate(alice, jobA);
        generate(alice, jobB);

        list(alice, "jobId", jobA).andExpect(jsonPath("$.totalElements").value(GENERATED));
        list(alice).andExpect(jsonPath("$.totalElements").value(2 * GENERATED));
        list(alice, "size", "10", "page", "2")
            .andExpect(jsonPath("$.content.length()").value(2 * GENERATED - 20))
            .andExpect(jsonPath("$.totalPages").value(3));
        list(alice, "category", "SILLY").andExpect(status().isBadRequest());
    }

    @Test
    void usersCannotSeeGenerateOrChangeEachOthersQuestions() throws Exception {
        String alicesJob = createJob(alice, "Platform Engineer");
        generate(alice, alicesJob);
        String id = firstQuestionId(alice);

        generate(bob, alicesJob).andExpect(status().isNotFound());
        list(bob).andExpect(jsonPath("$.totalElements").value(0));
        list(bob, "jobId", alicesJob).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(put("/api/interviews/questions/" + id).header("Authorization", bob)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PREPARED\"}")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/interviews/questions/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        list(alice).andExpect(jsonPath("$.totalElements").value(GENERATED));
    }

    @Test
    void deletingAJobRemovesItsQuestions() throws Exception {
        String jobId = createJob(alice, "Platform Engineer");
        generate(alice, jobId);
        assertThat(questions.count()).isEqualTo(GENERATED);
        mockMvc.perform(delete("/api/jobs/" + jobId).header("Authorization", alice)).andExpect(status().isNoContent());
        assertThat(questions.count()).isZero();
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/interviews/questions")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/jobs/" + UUID.randomUUID() + "/interview-questions/generate")).andExpect(status().isUnauthorized());
    }
}
