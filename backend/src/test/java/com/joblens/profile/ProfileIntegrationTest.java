package com.joblens.profile;

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
class ProfileIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired SkillRepository skills;

    private String alice;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        skills.deleteAll();
        alice = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "alice@example.com");
        bob = TestAuth.registerAndGetBearer(mockMvc, objectMapper, "bob@example.com");
    }

    private ResultActions putProfile(String bearer, String json) throws Exception {
        return mockMvc.perform(put("/api/users/me/profile").header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions addSkill(String bearer, String json) throws Exception {
        return mockMvc.perform(post("/api/users/me/skills").header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void newUserHasEmptyProfile() throws Exception {
        mockMvc.perform(get("/api/users/me/profile").header("Authorization", alice))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").doesNotExist())
            .andExpect(jsonPath("$.yearsOfExperience").doesNotExist());
    }

    @Test
    void profileCanBeCreatedThenUpdated() throws Exception {
        putProfile(alice, """
            {"name":"Alice Dev","headline":"Full-stack engineer","location":"Remote",
             "yearsOfExperience":5,"summary":"Builds things."}""")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Alice Dev"));

        putProfile(alice, "{\"name\":\"Alice B. Dev\",\"yearsOfExperience\":6}")
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me/profile").header("Authorization", alice))
            .andExpect(jsonPath("$.name").value("Alice B. Dev"))
            .andExpect(jsonPath("$.yearsOfExperience").value(6))
            .andExpect(jsonPath("$.headline").doesNotExist());
    }

    @Test
    void profilesAreIsolatedBetweenUsers() throws Exception {
        putProfile(alice, "{\"name\":\"Alice\"}").andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me/profile").header("Authorization", bob))
            .andExpect(jsonPath("$.name").doesNotExist());
    }

    @Test
    void profileValidationRejectsBadInput() throws Exception {
        putProfile(alice, "{\"yearsOfExperience\":-1,\"headline\":\"" + "x".repeat(161) + "\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.yearsOfExperience").value("Years of experience cannot be negative"))
            .andExpect(jsonPath("$.errors.headline").value("Headline must be at most 160 characters"));
    }

    @Test
    void profileEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/users/me/profile").contentType(MediaType.APPLICATION_JSON)
            .content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me/skills")).andExpect(status().isUnauthorized());
    }

    @Test
    void skillCanBeAddedListedAndRemoved() throws Exception {
        String body = addSkill(alice, "{\"name\":\"Java\",\"category\":\"LANGUAGE\",\"proficiency\":\"ADVANCED\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Java"))
            .andExpect(jsonPath("$.proficiency").value("ADVANCED"))
            .andReturn().getResponse().getContentAsString();
        String skillId = objectMapper.readTree(body).get("skillId").asText();

        mockMvc.perform(get("/api/users/me/skills").header("Authorization", alice))
            .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete("/api/users/me/skills/" + skillId).header("Authorization", alice))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users/me/skills").header("Authorization", alice))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void addingSameSkillAgainUpdatesProficiencyAndIsCaseInsensitive() throws Exception {
        addSkill(alice, "{\"name\":\"React\",\"category\":\"FRAMEWORK\",\"proficiency\":\"BEGINNER\"}")
            .andExpect(status().isOk());
        addSkill(alice, "{\"name\":\"  react \",\"proficiency\":\"EXPERT\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("React"))
            .andExpect(jsonPath("$.proficiency").value("EXPERT"));

        mockMvc.perform(get("/api/users/me/skills").header("Authorization", alice))
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void skillsAreSharedInCatalogButOwnedPerUser() throws Exception {
        addSkill(alice, "{\"name\":\"Docker\",\"proficiency\":\"ADVANCED\"}").andExpect(status().isOk());
        addSkill(bob, "{\"name\":\"docker\",\"proficiency\":\"BEGINNER\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me/skills").header("Authorization", alice))
            .andExpect(jsonPath("$[0].proficiency").value("ADVANCED"));
        mockMvc.perform(get("/api/users/me/skills").header("Authorization", bob))
            .andExpect(jsonPath("$[0].proficiency").value("BEGINNER"));
    }

    @Test
    void userCannotRemoveAnotherUsersSkill() throws Exception {
        String body = addSkill(alice, "{\"name\":\"AWS\",\"proficiency\":\"ADVANCED\"}")
            .andReturn().getResponse().getContentAsString();
        String skillId = objectMapper.readTree(body).get("skillId").asText();

        mockMvc.perform(delete("/api/users/me/skills/" + skillId).header("Authorization", bob))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users/me/skills").header("Authorization", alice))
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void skillValidationRejectsBadInput() throws Exception {
        addSkill(alice, "{\"name\":\"  \"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.name").value("Skill name is required"))
            .andExpect(jsonPath("$.errors.proficiency").value("Proficiency is required"));
    }

    @Test
    void suggestionsMatchByPrefixAndEscapeWildcards() throws Exception {
        addSkill(alice, "{\"name\":\"TypeScript\",\"proficiency\":\"ADVANCED\"}");
        addSkill(alice, "{\"name\":\"Tailwind CSS\",\"proficiency\":\"ADVANCED\"}");
        addSkill(alice, "{\"name\":\"Java\",\"proficiency\":\"ADVANCED\"}");

        mockMvc.perform(get("/api/skills/suggestions").param("q", "t").header("Authorization", bob))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/skills/suggestions").param("q", "%").header("Authorization", bob))
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/skills/suggestions").param("q", "").header("Authorization", bob))
            .andExpect(jsonPath("$.length()").value(0));
    }
}
