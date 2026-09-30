package com.joblens.common.error;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GlobalExceptionHandlerTest.ThrowingController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void notFoundMapsTo404ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/test/not-found").with(user("u")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Thing missing"));
    }

    @Test
    void conflictMapsTo409() throws Exception {
        mockMvc.perform(get("/api/test/conflict").with(user("u")))
            .andExpect(status().isConflict());
    }

    @Test
    void validationFailureListsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/test/validate").with(user("u"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.name").value("must not be blank"));
    }

    @Test
    void unexpectedErrorsDoNotLeakDetails() throws Exception {
        mockMvc.perform(get("/api/test/boom").with(user("u")))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("Something went wrong. Please try again later."));
    }

    record Payload(@NotBlank(message = "must not be blank") String name) { }

    @TestConfiguration
    @RestController
    static class ThrowingController {
        @GetMapping("/api/test/not-found")
        void notFound() { throw new NotFoundException("Thing missing"); }

        @GetMapping("/api/test/conflict")
        void conflict() { throw new ConflictException("Already exists"); }

        @GetMapping("/api/test/boom")
        void boom() { throw new IllegalStateException("secret internal detail"); }

        @PostMapping("/api/test/validate")
        void validate(@Valid @RequestBody Payload payload) { }
    }
}
