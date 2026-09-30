package com.joblens.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** End to end through the real pipeline (upload → parse → prompt → mock model → validate → store). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalysisIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired ResumeAnalysisRepository analyses;

    private String alice;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        alice = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "alice@example.com");
        bob = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "bob@example.com");
    }

    private String uploadResume(String bearer) throws Exception {
        String body = mockMvc.perform(multipart("/api/resumes")
                .file(new MockMultipartFile("file", "jane.pdf", "application/pdf", TestPdf.fromLines(TestPdf.SAMPLE_RESUME)))
                .header("Authorization", bearer))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createJob(String bearer, String description) throws Exception {
        String body = mockMvc.perform(post("/api/jobs").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                    "company", "Globex", "title", "Platform Engineer", "jobDescription", description))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private ResultActions analyze(String bearer, String jobId, String json) throws Exception {
        var request = post("/api/jobs/" + jobId + "/analyze").header("Authorization", bearer);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private static final String DESCRIPTION =
        "5+ years of experience. You will work with Java, Spring Boot, Kubernetes and Docker.";

    @Test
    void analyzesResumeAgainstJobAndStoresTheValidatedResult() throws Exception {
        uploadResume(alice);
        String jobId = createJob(alice, DESCRIPTION);

        analyze(alice, jobId, null)
            .andExpect(status().isCreated())
            // Resume has Java + Spring Boot; job wants Java, Spring Boot, Kubernetes, Docker.
            .andExpect(jsonPath("$.overallScore").value(50))
            .andExpect(jsonPath("$.matchingSkills[0]").value("Java"))
            .andExpect(jsonPath("$.matchingSkills[1]").value("Spring Boot"))
            .andExpect(jsonPath("$.missingSkills[0]").value("Docker"))
            .andExpect(jsonPath("$.missingSkills[1]").value("Kubernetes"))
            .andExpect(jsonPath("$.experienceAssessment").value(org.hamcrest.Matchers.containsString("5 years")))
            .andExpect(jsonPath("$.suggestions").isNotEmpty())
            .andExpect(jsonPath("$.interviewTopics").isNotEmpty())
            .andExpect(jsonPath("$.model").value("mock-analyzer"))
            .andExpect(jsonPath("$.resumeVersion").value(1));

        assertThat(analyses.count()).isEqualTo(1);
    }

    @Test
    void usesTheNewestResumeByDefaultAndAnExplicitOneWhenGiven() throws Exception {
        String first = uploadResume(alice);
        uploadResume(alice);
        String jobId = createJob(alice, DESCRIPTION);

        analyze(alice, jobId, null).andExpect(jsonPath("$.resumeVersion").value(2));
        analyze(alice, jobId, "{\"resumeId\":\"" + first + "\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resumeVersion").value(1))
            .andExpect(jsonPath("$.resumeId").value(first));
    }

    @Test
    void requiresAResume() throws Exception {
        String jobId = createJob(alice, DESCRIPTION);
        analyze(alice, jobId, null)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("Upload a resume before running an analysis."));
        assertThat(analyses.count()).isZero();
    }

    @Test
    void analysesListAndDetailAreAvailableNewestFirst() throws Exception {
        uploadResume(alice);
        String jobId = createJob(alice, DESCRIPTION);
        analyze(alice, jobId, null).andExpect(status().isCreated());
        String second = analyze(alice, jobId, null).andReturn().getResponse().getContentAsString();
        String secondId = objectMapper.readTree(second).get("id").asText();

        mockMvc.perform(get("/api/jobs/" + jobId + "/analyses").header("Authorization", alice))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(secondId))
            .andExpect(jsonPath("$[0].overallScore").value(50));
        mockMvc.perform(get("/api/analyses/" + secondId).header("Authorization", alice))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matchingSkills[0]").value("Java"));
    }

    @Test
    void usersCannotAnalyzeOrReadEachOthersData() throws Exception {
        uploadResume(alice);
        String alicesJob = createJob(alice, DESCRIPTION);
        String aliceAnalysis = objectMapper.readTree(
            analyze(alice, alicesJob, null).andReturn().getResponse().getContentAsString()).get("id").asText();
        String bobsResume = uploadResume(bob);

        analyze(bob, alicesJob, null).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/jobs/" + alicesJob + "/analyses").header("Authorization", bob)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/analyses/" + aliceAnalysis).header("Authorization", bob)).andExpect(status().isNotFound());
        // Using another user's resume id with your own job is also refused.
        String bobsJob = createJob(bob, DESCRIPTION);
        String alicesResume = objectMapper.readTree(mockMvc.perform(get("/api/resumes").header("Authorization", alice))
            .andReturn().getResponse().getContentAsString()).get(0).get("id").asText();
        analyze(bob, bobsJob, "{\"resumeId\":\"" + alicesResume + "\"}").andExpect(status().isNotFound());
        analyze(bob, bobsJob, "{\"resumeId\":\"" + bobsResume + "\"}").andExpect(status().isCreated());
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/jobs/" + UUID.randomUUID() + "/analyze")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/analyses/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void analysisSurvivesResumeDeletionAndDiesWithItsJob() throws Exception {
        String resumeId = uploadResume(alice);
        String jobId = createJob(alice, DESCRIPTION);
        String analysisId = objectMapper.readTree(
            analyze(alice, jobId, null).andReturn().getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/resumes/" + resumeId).header("Authorization", alice)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/analyses/" + analysisId).header("Authorization", alice))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resumeId").doesNotExist())
            .andExpect(jsonPath("$.resumeVersion").value(1));

        mockMvc.perform(delete("/api/jobs/" + jobId).header("Authorization", alice)).andExpect(status().isNoContent());
        assertThat(analyses.count()).isZero();
    }

    @Test
    void jobDescriptionWithNoKnownTechnologiesStillProducesAValidAnalysis() throws Exception {
        uploadResume(alice);
        String jobId = createJob(alice, "Looking for a motivated teammate who enjoys collaboration.");
        analyze(alice, jobId, null)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.overallScore").value(50))
            .andExpect(jsonPath("$.missingSkills.length()").value(0));
    }
}
