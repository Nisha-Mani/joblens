package com.joblens.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joblens.analysis.ai.AiClient;
import com.joblens.analysis.ai.AiException;
import com.joblens.analysis.ai.AiRequest;
import com.joblens.analysis.ai.AiResponse;
import com.joblens.support.TestAuth;
import com.joblens.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.ai.rate-limit-per-hour=2")
class InterviewGenerationFailureTest {

    private static final String VALID = """
        {"questions":[{"question":"Explain Docker layers","category":"TECHNICAL","difficulty":"MEDIUM","skills":["Docker"]}]}""";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired InterviewQuestionRepository questions;
    @MockitoBean AiClient aiClient;

    private String bearer;
    private String jobId;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        bearer = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "alice@example.com");
        jobId = objectMapper.readTree(mockMvc.perform(post("/api/jobs").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\":\"A\",\"title\":\"T\",\"jobDescription\":\"Docker\"}"))
            .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions generate() throws Exception {
        return mockMvc.perform(post("/api/jobs/" + jobId + "/interview-questions/generate").header("Authorization", bearer));
    }

    @Test
    void validOutputIsStored() throws Exception {
        when(aiClient.complete(any())).thenReturn(new AiResponse(VALID, "m"));
        generate().andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(1));
        assertThat(questions.count()).isEqualTo(1);
    }

    @Test
    void unusableOutputIsRetriedOnceThenRejectedWithNothingStored() throws Exception {
        when(aiClient.complete(any())).thenReturn(new AiResponse("{\"questions\":[{\"question\":\"x\",\"category\":\"NOPE\",\"difficulty\":\"EASY\",\"skills\":[]}]}", "m"));
        generate().andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.detail").value("The AI service returned an unusable response. Please try again."));
        verify(aiClient, times(2)).complete(any(AiRequest.class));
        assertThat(questions.count()).isZero();
    }

    @Test
    void timeoutsAndMissingConfigurationAreReportedClearly() throws Exception {
        doThrow(AiException.timeout()).when(aiClient).complete(any());
        generate().andExpect(status().isGatewayTimeout());
        doThrow(AiException.notConfigured()).when(aiClient).complete(any());
        generate().andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.detail").value("AI analysis is not configured on this server."));
    }

    @Test
    void sharesTheAiRateLimitWithAnalyses() throws Exception {
        when(aiClient.complete(any())).thenReturn(new AiResponse(VALID, "m"));
        generate().andExpect(status().isCreated());
        generate().andExpect(status().isCreated());
        generate().andExpect(status().isTooManyRequests());
        mockMvc.perform(get("/api/interviews/questions").header("Authorization", bearer)).andExpect(status().isOk());
    }
}
