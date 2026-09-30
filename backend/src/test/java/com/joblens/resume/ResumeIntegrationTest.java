package com.joblens.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joblens.common.storage.FileStorage;
import com.joblens.support.TestAuth;
import com.joblens.support.TestPdf;
import com.joblens.user.UserRepository;
import java.nio.charset.StandardCharsets;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired ResumeRepository resumes;
    @Autowired FileStorage storage;

    private String alice;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        alice = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "alice@example.com");
        bob = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "bob@example.com");
    }

    private ResultActions upload(String bearer, String fileName, String contentType, byte[] content)
            throws Exception {
        return mockMvc.perform(multipart("/api/resumes")
            .file(new MockMultipartFile("file", fileName, contentType, content))
            .header("Authorization", bearer));
    }

    private ResultActions uploadSample(String bearer) throws Exception {
        return upload(bearer, "jane.pdf", "application/pdf", TestPdf.fromLines(TestPdf.SAMPLE_RESUME));
    }

    private String uploadAndGetId(String bearer) throws Exception {
        String body = uploadSample(bearer).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    @Test
    void uploadParsesAndReturnsStructuredData() throws Exception {
        uploadSample(alice)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.fileName").value("jane.pdf"))
            .andExpect(jsonPath("$.parsed.name").value("Jane Developer"))
            .andExpect(jsonPath("$.parsed.email").value("jane.dev@example.com"))
            .andExpect(jsonPath("$.parsed.skills").isArray())
            .andExpect(jsonPath("$.parsed.certifications[0]").value("AWS Certified Developer"))
            .andExpect(jsonPath("$.extractedText").doesNotExist());
    }

    @Test
    void eachUploadCreatesANewVersion() throws Exception {
        uploadAndGetId(alice);
        uploadSample(alice).andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(get("/api/resumes").header("Authorization", alice))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].version").value(2));
        // Versions are per user.
        uploadSample(bob).andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void rejectsNonPdfContentType() throws Exception {
        upload(alice, "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("Only PDF files are supported"));
    }

    @Test
    void rejectsFileThatClaimsToBePdfButIsNot() throws Exception {
        upload(alice, "fake.pdf", "application/pdf", "<html>not a pdf</html>".getBytes(StandardCharsets.UTF_8))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("The file does not look like a valid PDF"));
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        upload(alice, "empty.pdf", "application/pdf", new byte[0]).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectsImageOnlyPdf() throws Exception {
        upload(alice, "scan.pdf", "application/pdf", TestPdf.blankPage())
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("No text found")));
    }

    @Test
    void rejectsOversizedFiles() throws Exception {
        byte[] big = new byte[6 * 1024 * 1024];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, big, 0, 5);
        upload(alice, "big.pdf", "application/pdf", big)
            .andExpect(status().isContentTooLarge())
            .andExpect(jsonPath("$.detail").value("The file is too large. Maximum size is 5 MB."));
        assertThat(resumes.count()).isZero();
    }

    @Test
    void uploadedFileNameIsSanitised() throws Exception {
        upload(alice, "../../etc/evil.pdf", "application/pdf", TestPdf.fromLines(TestPdf.SAMPLE_RESUME))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fileName").value("evil.pdf"));
    }

    @Test
    void parsedDataCanBeEditedAndPersists() throws Exception {
        String id = uploadAndGetId(alice);
        mockMvc.perform(put("/api/resumes/" + id + "/parsed").header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Jane Q. Developer","email":"jane@new.example","skills":["Java","  ","Rust"],
                     "experience":["Staff Engineer"]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.parsed.name").value("Jane Q. Developer"));

        mockMvc.perform(get("/api/resumes/" + id).header("Authorization", alice))
            .andExpect(jsonPath("$.parsed.email").value("jane@new.example"))
            .andExpect(jsonPath("$.parsed.skills.length()").value(2))
            .andExpect(jsonPath("$.parsed.phone").doesNotExist());
    }

    @Test
    void editedDataIsValidated() throws Exception {
        String id = uploadAndGetId(alice);
        mockMvc.perform(put("/api/resumes/" + id + "/parsed").header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-an-email\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.email").value("Enter a valid email address"));
    }

    @Test
    void deleteRemovesRowAndStoredFile() throws Exception {
        String id = uploadAndGetId(alice);
        String key = resumes.findById(UUID.fromString(id)).orElseThrow().getStorageKey();
        assertThat(storage.load(key)).isNotEmpty();

        mockMvc.perform(delete("/api/resumes/" + id).header("Authorization", alice))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/resumes/" + id).header("Authorization", alice))
            .andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.load(key)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void usersCannotAccessEachOthersResumes() throws Exception {
        String id = uploadAndGetId(alice);
        mockMvc.perform(get("/api/resumes/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/resumes/" + id + "/parsed").header("Authorization", bob)
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/resumes/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/resumes").header("Authorization", bob))
            .andExpect(jsonPath("$.length()").value(0));
        // Still intact for the owner.
        mockMvc.perform(get("/api/resumes/" + id).header("Authorization", alice)).andExpect(status().isOk());
    }

    @Test
    void resumeEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/resumes")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/resumes").file(new MockMultipartFile("file", "a.pdf",
            "application/pdf", TestPdf.fromLines(TestPdf.SAMPLE_RESUME)))).andExpect(status().isUnauthorized());
    }
}
