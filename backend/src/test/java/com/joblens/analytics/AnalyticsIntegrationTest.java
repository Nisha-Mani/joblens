package com.joblens.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joblens.support.TestAuth;
import com.joblens.support.TestPdf;
import com.joblens.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
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
class AnalyticsIntegrationTest {

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

    private String createJob(String bearer, String company, String description) throws Exception {
        String body = mockMvc.perform(post("/api/jobs").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("company", company, "title", "Engineer " + company,
                    "jobDescription", description))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    /** Creates a job and an application in the first status, then walks it through the rest. */
    private String track(String bearer, String company, String... statuses) throws Exception {
        String jobId = createJob(bearer, company, "Some description");
        String body = mockMvc.perform(post("/api/applications").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\":\"%s\",\"status\":\"%s\"}".formatted(jobId, statuses[0])))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();
        for (int i = 1; i < statuses.length; i++) {
            mockMvc.perform(patch("/api/applications/" + id + "/status").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + statuses[i] + "\"}"))
                .andExpect(status().isOk());
        }
        return id;
    }

    private void update(String bearer, String id, String status, String appliedAt, String interviewDate) throws Exception {
        mockMvc.perform(put("/api/applications/" + id).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"%s\",\"appliedAt\":%s,\"interviewDate\":%s}".formatted(status,
                    appliedAt == null ? "null" : "\"" + appliedAt + "\"",
                    interviewDate == null ? "null" : "\"" + interviewDate + "\"")))
            .andExpect(status().isOk());
    }

    private ResultActions dashboard(String bearer, String... params) throws Exception {
        var request = get("/api/analytics/dashboard").header("Authorization", bearer);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    @Test
    void newUserGetsZerosNullRatesAndEmptyLists() throws Exception {
        dashboard(alice)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totals.tracked").value(0))
            .andExpect(jsonPath("$.totals.applied").value(0))
            .andExpect(jsonPath("$.totals.interviews").value(0))
            // "no data" must be distinguishable from a real 0%.
            .andExpect(jsonPath("$.rates.responseRate").doesNotExist())
            .andExpect(jsonPath("$.rates.interviewRate").doesNotExist())
            .andExpect(jsonPath("$.statusDistribution.length()").value(7))
            .andExpect(jsonPath("$.statusDistribution[0].status").value("SAVED"))
            .andExpect(jsonPath("$.statusDistribution[0].count").value(0))
            .andExpect(jsonPath("$.applicationsByMonth.length()").value(12))
            .andExpect(jsonPath("$.upcomingInterviews.length()").value(0))
            .andExpect(jsonPath("$.recentApplications.length()").value(0))
            .andExpect(jsonPath("$.topMissingSkills.length()").value(0));
    }

    @Test
    void funnelMetricsUseStatusHistoryNotJustCurrentStatus() throws Exception {
        track(alice, "A", "SAVED", "APPLIED", "SCREENING", "INTERVIEW", "OFFER");
        track(alice, "B", "APPLIED", "REJECTED");
        track(alice, "C", "SAVED");
        // Reached an interview, then was rejected: still counts as an interview.
        track(alice, "D", "APPLIED", "INTERVIEW", "REJECTED");
        // Withdrawn by the user is not a company response.
        track(alice, "E", "APPLIED", "WITHDRAWN");

        dashboard(alice)
            .andExpect(jsonPath("$.totals.tracked").value(5))
            .andExpect(jsonPath("$.totals.applied").value(4))
            .andExpect(jsonPath("$.totals.responded").value(3))
            .andExpect(jsonPath("$.totals.interviews").value(2))
            .andExpect(jsonPath("$.totals.offers").value(1))
            .andExpect(jsonPath("$.totals.rejections").value(2))
            .andExpect(jsonPath("$.rates.responseRate").value(closeTo(0.75, 1e-9)))
            .andExpect(jsonPath("$.rates.interviewRate").value(closeTo(0.5, 1e-9)))
            .andExpect(jsonPath("$.rates.offerRate").value(closeTo(0.25, 1e-9)));
    }

    @Test
    void statusDistributionUsesCurrentStatusAndIncludesEveryStatus() throws Exception {
        track(alice, "A", "APPLIED");
        track(alice, "B", "APPLIED");
        track(alice, "C", "OFFER");
        dashboard(alice)
            .andExpect(jsonPath("$.statusDistribution.length()").value(7))
            .andExpect(jsonPath("$.statusDistribution[?(@.status=='APPLIED')].count").value(2))
            .andExpect(jsonPath("$.statusDistribution[?(@.status=='OFFER')].count").value(1))
            .andExpect(jsonPath("$.statusDistribution[?(@.status=='REJECTED')].count").value(0));
    }

    @Test
    void appliedThisMonthAndMonthlySeriesUseAppliedDates() throws Exception {
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        String thisMonth = track(alice, "A", "APPLIED");
        String alsoThisMonth = track(alice, "B", "APPLIED");
        String twoMonthsAgo = track(alice, "C", "APPLIED");
        String oldOne = track(alice, "D", "APPLIED");
        update(alice, thisMonth, "APPLIED", now.atDay(1).toString(), null);
        update(alice, alsoThisMonth, "APPLIED", now.atEndOfMonth().toString(), null);
        update(alice, twoMonthsAgo, "APPLIED", now.minusMonths(2).atDay(15).toString(), null);
        update(alice, oldOne, "APPLIED", now.minusMonths(20).atDay(1).toString(), null);

        dashboard(alice, "months", "6")
            .andExpect(jsonPath("$.totals.appliedThisMonth").value(2))
            .andExpect(jsonPath("$.applicationsByMonth.length()").value(6))
            .andExpect(jsonPath("$.applicationsByMonth[5].month").value(now.toString()))
            .andExpect(jsonPath("$.applicationsByMonth[5].count").value(2))
            .andExpect(jsonPath("$.applicationsByMonth[4].count").value(0))
            .andExpect(jsonPath("$.applicationsByMonth[3].month").value(now.minusMonths(2).toString()))
            .andExpect(jsonPath("$.applicationsByMonth[3].count").value(1))
            .andExpect(jsonPath("$.applicationsByMonth[0].month").value(now.minusMonths(5).toString()));
        // The 20-month-old application only appears once the window is wide enough.
        dashboard(alice, "months", "24")
            .andExpect(jsonPath("$.applicationsByMonth.length()").value(24))
            .andExpect(jsonPath("$.applicationsByMonth[?(@.month=='" + now.minusMonths(20) + "')].count").value(1));
    }

    @Test
    void monthsParameterIsClamped() throws Exception {
        dashboard(alice, "months", "0").andExpect(jsonPath("$.applicationsByMonth.length()").value(1));
        dashboard(alice, "months", "500").andExpect(jsonPath("$.applicationsByMonth.length()").value(24));
        dashboard(alice, "months", "abc").andExpect(status().isBadRequest());
    }

    @Test
    void upcomingInterviewsAreFutureOnlySortedAndExcludeClosedApplications() throws Exception {
        Instant now = Instant.now();
        String soon = track(alice, "Soon", "INTERVIEW");
        String later = track(alice, "Later", "INTERVIEW");
        String past = track(alice, "Past", "INTERVIEW");
        String rejected = track(alice, "Rejected", "INTERVIEW");
        update(alice, soon, "INTERVIEW", null, now.plus(2, ChronoUnit.DAYS).toString());
        update(alice, later, "INTERVIEW", null, now.plus(9, ChronoUnit.DAYS).toString());
        update(alice, past, "INTERVIEW", null, now.minus(3, ChronoUnit.DAYS).toString());
        update(alice, rejected, "REJECTED", null, now.plus(1, ChronoUnit.DAYS).toString());

        dashboard(alice)
            .andExpect(jsonPath("$.upcomingInterviews.length()").value(2))
            .andExpect(jsonPath("$.upcomingInterviews[0].company").value("Soon"))
            .andExpect(jsonPath("$.upcomingInterviews[1].company").value("Later"));
    }

    @Test
    void upcomingAndRecentListsAreCapped() throws Exception {
        Instant now = Instant.now();
        for (int i = 1; i <= 7; i++) {
            String id = track(alice, "Company " + i, "INTERVIEW");
            update(alice, id, "INTERVIEW", null, now.plus(i, ChronoUnit.DAYS).toString());
        }
        dashboard(alice)
            .andExpect(jsonPath("$.upcomingInterviews.length()").value(5))
            .andExpect(jsonPath("$.recentApplications.length()").value(5))
            .andExpect(jsonPath("$.upcomingInterviews[0].company").value("Company 1"));
    }

    @Test
    void recentApplicationsAreMostRecentlyUpdatedFirst() throws Exception {
        String first = track(alice, "First", "SAVED");
        track(alice, "Second", "SAVED");
        mockMvc.perform(patch("/api/applications/" + first + "/status").header("Authorization", alice)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPLIED\"}")).andExpect(status().isOk());
        dashboard(alice)
            .andExpect(jsonPath("$.recentApplications[0].company").value("First"))
            .andExpect(jsonPath("$.recentApplications[0].status").value("APPLIED"))
            .andExpect(jsonPath("$.recentApplications[1].company").value("Second"));
    }

    @Test
    void topMissingSkillsCountOnlyEachJobsLatestAnalysis() throws Exception {
        mockMvc.perform(multipart("/api/resumes")
            .file(new MockMultipartFile("file", "jane.pdf", "application/pdf", TestPdf.fromLines(TestPdf.SAMPLE_RESUME)))
            .header("Authorization", alice)).andExpect(status().isCreated());
        // The resume has Java and AWS; everything else in these postings is missing.
        String job1 = createJob(alice, "One", "Java, Docker and Kubernetes.");
        String job2 = createJob(alice, "Two", "Docker and Terraform.");
        for (String job : new String[] {job1, job2, job1, job1}) {
            mockMvc.perform(post("/api/jobs/" + job + "/analyze").header("Authorization", alice)).andExpect(status().isCreated());
        }
        // Job one was analysed three times but must count once: Docker 2, Kubernetes 1, Terraform 1.
        dashboard(alice)
            .andExpect(jsonPath("$.topMissingSkills.length()").value(3))
            .andExpect(jsonPath("$.topMissingSkills[0].skill").value("Docker"))
            .andExpect(jsonPath("$.topMissingSkills[0].count").value(2))
            .andExpect(jsonPath("$.topMissingSkills[1].skill").value("Kubernetes"))
            .andExpect(jsonPath("$.topMissingSkills[1].count").value(1))
            .andExpect(jsonPath("$.topMissingSkills[2].skill").value("Terraform"));
    }

    @Test
    void oneUsersDataNeverLeaksIntoAnothersDashboard() throws Exception {
        track(alice, "A", "APPLIED", "INTERVIEW", "OFFER");
        track(bob, "B", "SAVED");
        dashboard(bob)
            .andExpect(jsonPath("$.totals.tracked").value(1))
            .andExpect(jsonPath("$.totals.applied").value(0))
            .andExpect(jsonPath("$.totals.offers").value(0))
            .andExpect(jsonPath("$.recentApplications.length()").value(1))
            .andExpect(jsonPath("$.recentApplications[0].company").value("B"));
        dashboard(alice).andExpect(jsonPath("$.totals.offers").value(1));
    }

    @Test
    void deletingAnApplicationRemovesItFromTheMetrics() throws Exception {
        String id = track(alice, "A", "APPLIED", "INTERVIEW");
        dashboard(alice).andExpect(jsonPath("$.totals.interviews").value(1));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/applications/" + id)
            .header("Authorization", alice)).andExpect(status().isNoContent());
        dashboard(alice)
            .andExpect(jsonPath("$.totals.tracked").value(0))
            .andExpect(jsonPath("$.totals.interviews").value(0));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/analytics/dashboard")).andExpect(status().isUnauthorized());
        assertThat(LocalDate.now()).isNotNull();
    }
}
