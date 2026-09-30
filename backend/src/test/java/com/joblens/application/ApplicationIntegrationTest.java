package com.joblens.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joblens.support.TestAuth;
import com.joblens.user.UserRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
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
class ApplicationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired ApplicationRepository applications;

    private String alice;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        alice = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "alice@example.com");
        bob = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "bob@example.com");
    }

    private String createJob(String bearer, String company, String title) throws Exception {
        String body = mockMvc.perform(post("/api/jobs").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\":\"%s\",\"title\":\"%s\",\"jobDescription\":\"desc\"}".formatted(company, title)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private ResultActions createApp(String bearer, String json) throws Exception {
        return mockMvc.perform(post("/api/applications").header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String createAppFor(String bearer, String company, String title, String status) throws Exception {
        String jobId = createJob(bearer, company, title);
        String body = createApp(bearer, "{\"jobId\":\"%s\",\"status\":\"%s\"}".formatted(jobId, status))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private ResultActions list(String bearer, String... params) throws Exception {
        var request = get("/api/applications").header("Authorization", bearer);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    @Test
    void createSavedApplicationHasNoAppliedDateAndOneHistoryEntry() throws Exception {
        String jobId = createJob(alice, "Acme", "Engineer");
        createApp(alice, "{\"jobId\":\"%s\",\"status\":\"SAVED\"}".formatted(jobId))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.company").value("Acme"))
            .andExpect(jsonPath("$.status").value("SAVED"))
            .andExpect(jsonPath("$.appliedAt").doesNotExist())
            .andExpect(jsonPath("$.history.length()").value(1))
            .andExpect(jsonPath("$.history[0].to").value("SAVED"))
            .andExpect(jsonPath("$.history[0].from").doesNotExist());
    }

    @Test
    void creatingAsAppliedDefaultsAppliedDateToToday() throws Exception {
        String jobId = createJob(alice, "Acme", "Engineer");
        createApp(alice, "{\"jobId\":\"%s\",\"status\":\"APPLIED\"}".formatted(jobId))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.appliedAt").value(LocalDate.now(ZoneOffset.UTC).toString()));
    }

    @Test
    void explicitAppliedDateAndInterviewAndNotesAreStored() throws Exception {
        String jobId = createJob(alice, "Acme", "Engineer");
        createApp(alice, """
            {"jobId":"%s","status":"INTERVIEW","appliedAt":"2026-08-01",
             "interviewDate":"2026-09-15T14:00:00Z","notes":"  Spoke with recruiter  "}""".formatted(jobId))
            .andExpect(jsonPath("$.appliedAt").value("2026-08-01"))
            .andExpect(jsonPath("$.interviewDate").value("2026-09-15T14:00:00Z"))
            .andExpect(jsonPath("$.notes").value("Spoke with recruiter"));
    }

    @Test
    void oneApplicationPerJob() throws Exception {
        String jobId = createJob(alice, "Acme", "Engineer");
        createApp(alice, "{\"jobId\":\"%s\",\"status\":\"SAVED\"}".formatted(jobId)).andExpect(status().isCreated());
        createApp(alice, "{\"jobId\":\"%s\",\"status\":\"APPLIED\"}".formatted(jobId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("This job already has an application"));
    }

    @Test
    void cannotCreateApplicationForAnotherUsersJobOrMissingJob() throws Exception {
        String alicesJob = createJob(alice, "Acme", "Engineer");
        createApp(bob, "{\"jobId\":\"%s\",\"status\":\"SAVED\"}".formatted(alicesJob)).andExpect(status().isNotFound());
        createApp(bob, "{\"jobId\":\"%s\",\"status\":\"SAVED\"}".formatted(UUID.randomUUID())).andExpect(status().isNotFound());
        createApp(bob, "{\"status\":\"SAVED\"}").andExpect(status().isBadRequest());
    }

    @Test
    void validatesStatusAndNotes() throws Exception {
        String jobId = createJob(alice, "Acme", "Engineer");
        createApp(alice, "{\"jobId\":\"%s\"}".formatted(jobId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.status").value("Status is required"));
        createApp(alice, "{\"jobId\":\"%s\",\"status\":\"MAYBE\"}".formatted(jobId)).andExpect(status().isBadRequest());
        createApp(alice, "{\"jobId\":\"%s\",\"status\":\"SAVED\",\"notes\":\"%s\"}".formatted(jobId, "x".repeat(10001)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.notes").value("Notes must be at most 10000 characters"));
    }

    @Test
    void statusChangesAreRecordedInHistoryInOrder() throws Exception {
        String id = createAppFor(alice, "Acme", "Engineer", "SAVED");
        for (String next : new String[] {"APPLIED", "INTERVIEW", "REJECTED"}) {
            mockMvc.perform(patch("/api/applications/" + id + "/status").header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + next + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(next));
        }
        mockMvc.perform(get("/api/applications/" + id).header("Authorization", alice))
            .andExpect(jsonPath("$.history.length()").value(4))
            .andExpect(jsonPath("$.history[1].from").value("SAVED"))
            .andExpect(jsonPath("$.history[1].to").value("APPLIED"))
            .andExpect(jsonPath("$.history[2].to").value("INTERVIEW"))
            .andExpect(jsonPath("$.history[3].to").value("REJECTED"))
            // Applied date was filled in when it first left SAVED.
            .andExpect(jsonPath("$.appliedAt").isNotEmpty());
    }

    @Test
    void settingTheSameStatusAgainDoesNotAddHistory() throws Exception {
        String id = createAppFor(alice, "Acme", "Engineer", "APPLIED");
        mockMvc.perform(patch("/api/applications/" + id + "/status").header("Authorization", alice)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPLIED\"}")).andExpect(status().isOk());
        mockMvc.perform(get("/api/applications/" + id).header("Authorization", alice))
            .andExpect(jsonPath("$.history.length()").value(1));
    }

    @Test
    void updateChangesFieldsAndRecordsStatusChange() throws Exception {
        String id = createAppFor(alice, "Acme", "Engineer", "APPLIED");
        mockMvc.perform(put("/api/applications/" + id).header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SCREENING\",\"appliedAt\":\"2026-07-04\",\"interviewDate\":\"2026-10-01T09:30:00Z\",\"notes\":\"Call booked\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SCREENING"))
            .andExpect(jsonPath("$.appliedAt").value("2026-07-04"))
            .andExpect(jsonPath("$.notes").value("Call booked"))
            .andExpect(jsonPath("$.history.length()").value(2));
    }

    @Test
    void deleteRemovesApplicationButKeepsJob() throws Exception {
        String jobId = createJob(alice, "Acme", "Engineer");
        String body = createApp(alice, "{\"jobId\":\"%s\",\"status\":\"SAVED\"}".formatted(jobId))
            .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(delete("/api/applications/" + id).header("Authorization", alice)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/applications/" + id).header("Authorization", alice)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/jobs/" + jobId).header("Authorization", alice)).andExpect(status().isOk());
    }

    @Test
    void deletingAJobRemovesItsApplication() throws Exception {
        String jobId = createJob(alice, "Acme", "Engineer");
        createApp(alice, "{\"jobId\":\"%s\",\"status\":\"APPLIED\"}".formatted(jobId)).andExpect(status().isCreated());
        assertThat(applications.count()).isEqualTo(1);
        mockMvc.perform(delete("/api/jobs/" + jobId).header("Authorization", alice)).andExpect(status().isNoContent());
        assertThat(applications.count()).isZero();
    }

    @Test
    void usersCannotAccessEachOthersApplications() throws Exception {
        String id = createAppFor(alice, "Acme", "Engineer", "APPLIED");
        mockMvc.perform(get("/api/applications/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/applications/" + id).header("Authorization", bob)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OFFER\"}")).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/applications/" + id + "/status").header("Authorization", bob)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OFFER\"}")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/applications/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        list(bob).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/applications/" + id).header("Authorization", alice))
            .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/applications")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listFiltersByStatusAndSearchesCompanyAndTitle() throws Exception {
        createAppFor(alice, "Globex", "Platform Engineer", "APPLIED");
        createAppFor(alice, "Initech", "Designer", "INTERVIEW");
        createAppFor(alice, "Umbrella", "Engineer", "APPLIED");

        list(alice, "status", "APPLIED").andExpect(jsonPath("$.totalElements").value(2));
        list(alice, "q", "globex").andExpect(jsonPath("$.totalElements").value(1));
        list(alice, "q", "ENGINEER").andExpect(jsonPath("$.totalElements").value(2));
        list(alice, "q", "engineer", "status", "APPLIED").andExpect(jsonPath("$.totalElements").value(2));
        list(alice, "q", "%").andExpect(jsonPath("$.totalElements").value(0));
        list(alice, "status", "NOPE").andExpect(status().isBadRequest());
    }

    @Test
    void canFilterByJobAndOtherUsersJobsMatchNothing() throws Exception {
        String jobId = createJob(alice, "Acme", "Engineer");
        createApp(alice, "{\"jobId\":\"%s\",\"status\":\"SAVED\"}".formatted(jobId)).andExpect(status().isCreated());
        createAppFor(alice, "Other", "T", "APPLIED");
        list(alice, "jobId", jobId)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].company").value("Acme"));
        list(bob, "jobId", jobId).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sortsByCompanyAndPutsMissingInterviewDatesLast() throws Exception {
        String a = createAppFor(alice, "Beta", "T", "INTERVIEW");
        createAppFor(alice, "Alpha", "T", "APPLIED");
        mockMvc.perform(put("/api/applications/" + a).header("Authorization", alice)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"INTERVIEW\",\"interviewDate\":\"2026-11-01T10:00:00Z\"}")).andExpect(status().isOk());

        list(alice, "sortBy", "company", "direction", "asc")
            .andExpect(jsonPath("$.content[0].company").value("Alpha"));
        // Descending by interview date still lists the application without a date last.
        list(alice, "sortBy", "interviewDate", "direction", "desc")
            .andExpect(jsonPath("$.content[0].company").value("Beta"))
            .andExpect(jsonPath("$.content[1].company").value("Alpha"));
        list(alice, "sortBy", "interviewDate", "direction", "asc")
            .andExpect(jsonPath("$.content[0].company").value("Beta"));
    }

    @Test
    void rejectsUnknownSortField() throws Exception {
        list(alice, "sortBy", "notes").andExpect(status().isBadRequest());
        list(alice, "sortBy", "job.jobDescription").andExpect(status().isBadRequest());
    }

    @Test
    void paginatesOnTheServer() throws Exception {
        for (int i = 1; i <= 5; i++) {
            createAppFor(alice, "Company " + i, "T", "APPLIED");
        }
        list(alice, "sortBy", "company", "direction", "asc", "size", "2", "page", "1")
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.content[0].company").value("Company 3"));
    }
}
