package com.joblens.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** Registers a user through the real API and returns an Authorization header value. */
public final class TestAuth {

    public static final String PASSWORD = "correct-horse-battery";

    private TestAuth() { }

    public static String registerAndGetBearer(MockMvc mockMvc, ObjectMapper mapper, String email)
            throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
            .andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(body).get("token").asText();
    }
}
