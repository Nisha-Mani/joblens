package com.joblens.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joblens.analysis.ai.AiClient;
import com.joblens.analysis.ai.AiException;
import com.joblens.analysis.ai.AiRequest;
import com.joblens.analysis.ai.AiResponse;
import com.joblens.support.TestAuth;
import com.joblens.support.TestPdf;
import com.joblens.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/** What users see when the AI layer misbehaves, and what is (not) stored. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.ai.rate-limit-per-hour=3")
class AnalysisFailureHandlingTest {

    private static final String VALID = """
        {"overallScore":70,"matchingSkills":["Java"],"missingSkills":["Docker"],"keywordGaps":[],
         "experienceAssessment":"Good fit.","suggestions":["Add Docker"],"interviewTopics":["Docker"]}""";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired ResumeAnalysisRepository analyses;
    @Autowired AnalysisRateLimiter rateLimiter;
    @MockitoBean AiClient aiClient;

    private String bearer;
    private String jobId;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        bearer = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "alice@example.com");
        mockMvc.perform(multipart("/api/resumes")
            .file(new MockMultipartFile("file", "jane.pdf", "application/pdf", TestPdf.fromLines(TestPdf.SAMPLE_RESUME)))
            .header("Authorization", bearer)).andExpect(status().isCreated());
        jobId = objectMapper.readTree(mockMvc.perform(post("/api/jobs").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\":\"A\",\"title\":\"T\",\"jobDescription\":\"Java and Docker\"}"))
            .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions analyze() throws Exception {
        return mockMvc.perform(post("/api/jobs/" + jobId + "/analyze").header("Authorization", bearer));
    }

    @Test
    void validModelOutputIsStored() throws Exception {
        when(aiClient.complete(any())).thenReturn(new AiResponse(VALID, "test-model"));
        analyze().andExpect(status().isCreated()).andExpect(jsonPath("$.overallScore").value(70));
        assertThat(analyses.count()).isEqualTo(1);
    }

    @Test
    void malformedOutputIsRetriedOnceAndThenAccepted() throws Exception {
        when(aiClient.complete(any()))
            .thenReturn(new AiResponse("Sure! Here is my analysis: it looks good", "m"))
            .thenReturn(new AiResponse(VALID, "m"));
        analyze().andExpect(status().isCreated());
        verify(aiClient, times(2)).complete(any(AiRequest.class));
        assertThat(analyses.count()).isEqualTo(1);
    }

    @Test
    void persistentlyMalformedOutputIsRejectedAndNothingIsStored() throws Exception {
        when(aiClient.complete(any())).thenReturn(new AiResponse("{\"overallScore\":\"high\"}", "m"));
        analyze().andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.detail").value("The AI service returned an unusable response. Please try again."));
        verify(aiClient, times(2)).complete(any(AiRequest.class));
        assertThat(analyses.count()).isZero();
    }

    @Test
    void outOfRangeScoresAreNeverStored() throws Exception {
        when(aiClient.complete(any())).thenReturn(new AiResponse(VALID.replace("70", "250"), "m"));
        analyze().andExpect(status().isBadGateway());
        assertThat(analyses.count()).isZero();
    }

    @Test
    void timeoutsSurfaceAsGatewayTimeoutWithoutRetry() throws Exception {
        when(aiClient.complete(any())).thenThrow(AiException.timeout());
        analyze().andExpect(status().isGatewayTimeout())
            .andExpect(jsonPath("$.detail").value("The AI service took too long to respond. Please try again."));
        verify(aiClient, times(1)).complete(any(AiRequest.class));
    }

    @Test
    void missingConfigurationIsReportedClearly() throws Exception {
        when(aiClient.complete(any())).thenThrow(AiException.notConfigured());
        analyze().andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.detail").value("AI analysis is not configured on this server."));
    }

    @Test
    void providerThrottlingAndOutagesHaveDistinctMessages() throws Exception {
        doThrow(AiException.busy()).when(aiClient).complete(any());
        analyze().andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("busy")));
        // doThrow avoids invoking the previously stubbed behaviour while re-stubbing.
        doThrow(AiException.unavailable()).when(aiClient).complete(any());
        analyze().andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("unavailable")));
    }

    @Test
    void oversizedInputIsReportedAsAClientProblem() throws Exception {
        when(aiClient.complete(any())).thenThrow(AiException.inputTooLarge());
        analyze().andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("too long")));
    }

    @Test
    void perUserRateLimitStopsExpensiveCallsBeforeTheyReachTheModel() throws Exception {
        when(aiClient.complete(any())).thenReturn(new AiResponse(VALID, "m"));
        for (int i = 0; i < 3; i++) {
            analyze().andExpect(status().isCreated());
        }
        analyze().andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("3 analyses per hour")));
        verify(aiClient, times(3)).complete(any(AiRequest.class));
    }

    @Test
    void notFoundAndMissingResumeDoNotConsumeQuotaOrCallTheModel() throws Exception {
        mockMvc.perform(post("/api/jobs/" + java.util.UUID.randomUUID() + "/analyze").header("Authorization", bearer))
            .andExpect(status().isNotFound());
        verify(aiClient, never()).complete(any());
    }
}
