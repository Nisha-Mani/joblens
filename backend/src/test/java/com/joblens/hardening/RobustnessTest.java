package com.joblens.hardening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joblens.support.TestAuth;
import com.joblens.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** Bad input from clients must produce clear 4xx problems, never 500s or stack traces. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RobustnessTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;

    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        bearer = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "alice@example.com");
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/jobs").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{not json"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(""))
            .andExpect(status().isBadRequest());
    }

    @Test
    void wrongContentTypeIsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/jobs").header("Authorization", bearer)
                .contentType(MediaType.TEXT_PLAIN).content("company=x"))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void wrongMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/jobs").header("Authorization", bearer))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void malformedIdsAreBadRequestsNotServerErrors() throws Exception {
        mockMvc.perform(get("/api/jobs/not-a-uuid").header("Authorization", bearer)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/applications/123").header("Authorization", bearer)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/analyses/%00").header("Authorization", bearer)).andExpect(status().is4xxClientError());
    }

    @Test
    void wrongTypedFieldsAreBadRequests() throws Exception {
        mockMvc.perform(post("/api/jobs").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\":123,\"title\":[\"x\"],\"jobDescription\":{}}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void errorResponsesNeverExposeInternals() throws Exception {
        String body = mockMvc.perform(post("/api/jobs").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{not json"))
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception", "com.joblens", "at org.", "\tat ");
    }

    @Test
    void securityHeadersAreSetOnApiResponses() throws Exception {
        var response = mockMvc.perform(get("/api/ping")).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void authenticatedResponsesAreNotCacheable() throws Exception {
        var response = mockMvc.perform(get("/api/users/me").header("Authorization", bearer)).andReturn().getResponse();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void corsOnlyAllowsConfiguredOrigins() throws Exception {
        mockMvc.perform(get("/api/ping").header("Origin", "http://localhost:5173"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Access-Control-Allow-Origin", "http://localhost:5173"));
        var evil = mockMvc.perform(get("/api/ping").header("Origin", "https://evil.example")).andReturn().getResponse();
        assertThat(evil.getStatus()).isEqualTo(403);
        assertThat(evil.getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void oversizedJsonBodiesAreRejectedRatherThanStored() throws Exception {
        String hugeDescription = "x".repeat(25_000);
        mockMvc.perform(post("/api/jobs").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\":\"A\",\"title\":\"T\",\"jobDescription\":\"" + hugeDescription + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.jobDescription").exists());
    }
}
