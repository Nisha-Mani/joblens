package com.joblens.hardening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.joblens.resume.ResumeRepository;
import com.joblens.support.TestAuth;
import com.joblens.support.TestPdf;
import com.joblens.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Fires requests truly in parallel at the places where a check-then-insert could race. The rule
 * in every case: clients get clean 2xx/409 answers, never a 500, and the database ends consistent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrencyTest {

    private static final int THREADS = 8;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired ResumeRepository resumes;

    @BeforeEach
    void clean() {
        users.deleteAll();
    }

    /** Runs the task on {@code THREADS} threads released at the same instant; returns the HTTP statuses. */
    private List<Integer> inParallel(Callable<Integer> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get());
        }
        pool.shutdown();
        return statuses;
    }

    private static long count(List<Integer> statuses, int status) {
        return statuses.stream().filter(s -> s == status).count();
    }

    @Test
    void simultaneousRegistrationsOfOneEmailCreateExactlyOneAccount() throws Exception {
        List<Integer> statuses = inParallel(() -> mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"race@example.com\",\"password\":\"correct-horse\"}"))
            .andReturn().getResponse().getStatus());

        assertThat(count(statuses, 201)).isEqualTo(1);
        assertThat(count(statuses, 409)).isEqualTo(THREADS - 1);
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void simultaneousApplicationsForOneJobCreateExactlyOne() throws Exception {
        String bearer = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "alice@example.com");
        String jobId = objectMapper.readTree(mockMvc.perform(post("/api/jobs").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\":\"A\",\"title\":\"T\",\"jobDescription\":\"d\"}"))
            .andReturn().getResponse().getContentAsString()).get("id").asText();

        List<Integer> statuses = inParallel(() -> mockMvc.perform(post("/api/applications")
            .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"jobId\":\"%s\",\"status\":\"APPLIED\"}".formatted(jobId)))
            .andReturn().getResponse().getStatus());

        assertThat(count(statuses, 201)).isEqualTo(1);
        assertThat(count(statuses, 409)).isEqualTo(THREADS - 1);
        String total = mockMvc.perform(get("/api/applications").header("Authorization", bearer))
            .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(total).get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void simultaneousUploadsNeverReuseAVersionOrFailWithAServerError() throws Exception {
        String bearer = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "alice@example.com");
        byte[] pdf = TestPdf.fromLines(TestPdf.SAMPLE_RESUME);

        List<Integer> statuses = inParallel(() -> mockMvc.perform(multipart("/api/resumes")
            .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", pdf))
            .header("Authorization", bearer)).andReturn().getResponse().getStatus());

        assertThat(statuses).allMatch(s -> s == 201 || s == 409);
        assertThat(count(statuses, 201)).isGreaterThanOrEqualTo(1);
        // Every stored resume has its own version: no duplicates, and nothing was half-written.
        var stored = resumes.findAll();
        assertThat(stored).hasSize((int) count(statuses, 201));
        assertThat(stored.stream().map(r -> r.getVersion()).collect(Collectors.toSet())).hasSameSizeAs(stored);
    }

    @Test
    void simultaneousAddsOfTheSameNewSkillAllSucceedWithoutDuplicates() throws Exception {
        String bearer = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "alice@example.com");
        List<Integer> statuses = inParallel(() -> mockMvc.perform(post("/api/users/me/skills")
            .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Rustacean\",\"proficiency\":\"ADVANCED\"}"))
            .andReturn().getResponse().getStatus());

        assertThat(statuses).allMatch(s -> s == 200);
        String skills = mockMvc.perform(get("/api/users/me/skills").header("Authorization", bearer))
            .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(skills)).hasSize(1);
    }
}
