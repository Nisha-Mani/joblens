package com.joblens.job;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;

    private String alice;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        alice = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "alice@example.com");
        bob = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "bob@example.com");
    }

    private static String jobJson(String company, String title, String location, String type) {
        return """
            {"company":"%s","title":"%s","location":%s,"employmentType":%s,
             "jobDescription":"Build great things with Java and React."}"""
            .formatted(company, title, location == null ? "null" : "\"" + location + "\"",
                type == null ? "null" : "\"" + type + "\"");
    }

    private ResultActions create(String bearer, String json) throws Exception {
        return mockMvc.perform(post("/api/jobs").header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String createAndGetId(String bearer, String company, String title) throws Exception {
        String body = create(bearer, jobJson(company, title, "Remote", "FULL_TIME"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private ResultActions list(String bearer, String... params) throws Exception {
        var request = get("/api/jobs").header("Authorization", bearer);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    @Test
    void createReturnsJobWithDefaults() throws Exception {
        create(alice, jobJson("Acme", "Backend Engineer", null, null))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.company").value("Acme"))
            .andExpect(jsonPath("$.employmentType").value("FULL_TIME"))
            .andExpect(jsonPath("$.location").doesNotExist())
            .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void createValidatesInput() throws Exception {
        create(alice, "{\"company\":\" \",\"title\":\"\",\"jobDescription\":\"\",\"sourceUrl\":\"javascript:alert(1)\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.company").value("Company is required"))
            .andExpect(jsonPath("$.errors.title").value("Title is required"))
            .andExpect(jsonPath("$.errors.jobDescription").value("Job description is required"))
            .andExpect(jsonPath("$.errors.sourceUrl").value("Source URL must start with http:// or https://"));
    }

    @Test
    void acceptsHttpsSourceUrl() throws Exception {
        create(alice, """
            {"company":"A","title":"T","jobDescription":"d","sourceUrl":"https://example.com/jobs/1"}""")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sourceUrl").value("https://example.com/jobs/1"));
    }

    @Test
    void getUpdateAndDelete() throws Exception {
        String id = createAndGetId(alice, "Acme", "Engineer");

        mockMvc.perform(get("/api/jobs/" + id).header("Authorization", alice))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobDescription").value("Build great things with Java and React."));

        mockMvc.perform(put("/api/jobs/" + id).header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON).content(jobJson("Acme Inc", "Staff Engineer", "NYC", "CONTRACT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.company").value("Acme Inc"))
            .andExpect(jsonPath("$.employmentType").value("CONTRACT"));

        mockMvc.perform(delete("/api/jobs/" + id).header("Authorization", alice)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/jobs/" + id).header("Authorization", alice)).andExpect(status().isNotFound());
    }

    @Test
    void usersCannotAccessEachOthersJobs() throws Exception {
        String id = createAndGetId(alice, "Acme", "Engineer");
        mockMvc.perform(get("/api/jobs/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/jobs/" + id).header("Authorization", bob)
            .contentType(MediaType.APPLICATION_JSON).content(jobJson("X", "Y", null, null))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/jobs/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        list(bob).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/jobs/" + id).header("Authorization", alice)).andExpect(status().isOk());
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listIsNewestFirstByDefaultAndOmitsDescription() throws Exception {
        createAndGetId(alice, "First", "A");
        createAndGetId(alice, "Second", "B");
        list(alice)
            .andExpect(jsonPath("$.content[0].company").value("Second"))
            .andExpect(jsonPath("$.content[0].jobDescription").doesNotExist())
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void searchMatchesCompanyTitleAndLocationCaseInsensitively() throws Exception {
        create(alice, jobJson("Globex", "Platform Engineer", "Berlin", "FULL_TIME"));
        create(alice, jobJson("Initech", "Designer", "Austin", "FULL_TIME"));
        list(alice, "q", "globex").andExpect(jsonPath("$.totalElements").value(1));
        list(alice, "q", "DESIGN").andExpect(jsonPath("$.totalElements").value(1));
        list(alice, "q", "austin").andExpect(jsonPath("$.content[0].company").value("Initech"));
        list(alice, "q", "nomatch").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void searchTreatsWildcardsLiterally() throws Exception {
        create(alice, jobJson("100% Remote Co", "Engineer", null, null));
        create(alice, jobJson("Other", "Engineer", null, null));
        list(alice, "q", "%").andExpect(jsonPath("$.totalElements").value(1));
        list(alice, "q", "_").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByEmploymentType() throws Exception {
        create(alice, jobJson("A", "T", null, "CONTRACT"));
        create(alice, jobJson("B", "T", null, "FULL_TIME"));
        list(alice, "employmentType", "CONTRACT")
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].company").value("A"));
    }

    @Test
    void sortsByWhitelistedFields() throws Exception {
        create(alice, jobJson("Beta", "T", null, null));
        create(alice, jobJson("Alpha", "T", null, null));
        create(alice, jobJson("Gamma", "T", null, null));
        list(alice, "sortBy", "company", "direction", "asc")
            .andExpect(jsonPath("$.content[0].company").value("Alpha"))
            .andExpect(jsonPath("$.content[2].company").value("Gamma"));
        list(alice, "sortBy", "company", "direction", "desc")
            .andExpect(jsonPath("$.content[0].company").value("Gamma"));
    }

    @Test
    void rejectsUnknownSortFieldAndBadEnum() throws Exception {
        list(alice, "sortBy", "jobDescription; drop table jobs").andExpect(status().isBadRequest());
        list(alice, "employmentType", "SOMETIMES").andExpect(status().isBadRequest());
    }

    @Test
    void paginatesOnTheServer() throws Exception {
        for (int i = 1; i <= 5; i++) {
            create(alice, jobJson("Company " + i, "T", null, null));
        }
        list(alice, "sortBy", "company", "direction", "asc", "size", "2", "page", "0")
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.content[0].company").value("Company 1"));
        list(alice, "sortBy", "company", "direction", "asc", "size", "2", "page", "2")
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].company").value("Company 5"));
        list(alice, "size", "2", "page", "9").andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void clampsPageSizeAndNegativePage() throws Exception {
        create(alice, jobJson("A", "T", null, null));
        list(alice, "size", "100000", "page", "-3")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size").value(100))
            .andExpect(jsonPath("$.page").value(0));
    }
}
