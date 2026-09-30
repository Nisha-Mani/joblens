package com.joblens.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import com.joblens.user.Role;
import com.joblens.user.User;
import com.joblens.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecretKey jwtSigningKey;
    @Autowired JwtProperties jwtProperties;

    @BeforeEach
    void cleanUsers() {
        users.deleteAll();
    }

    private ResultActions postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private String registerAndGetToken(String email) throws Exception {
        String body = postJson("/api/auth/register", credentials(email, PASSWORD))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String forgeToken(SecretKey key, String subject, String issuer, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer).subject(subject)
            .issuedAt(expiresAt.minus(10, ChronoUnit.MINUTES)).expiresAt(expiresAt)
            .claim("role", "USER").build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(key))
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    }

    @Test
    void registerCreatesUserAndReturnsToken() throws Exception {
        postJson("/api/auth/register", credentials("New.User@Example.com", PASSWORD))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value("new.user@example.com"))
            .andExpect(jsonPath("$.user.role").value("USER"))
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        User saved = users.findByEmailIgnoreCase("new.user@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
    }

    @Test
    void duplicateRegistrationIsRejectedCaseInsensitively() throws Exception {
        registerAndGetToken("dup@example.com");
        postJson("/api/auth/register", credentials("DUP@example.com", PASSWORD))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("An account with this email already exists"));
    }

    @Test
    void registrationValidatesInput() throws Exception {
        postJson("/api/auth/register", credentials("not-an-email", "short"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.email").value("Enter a valid email address"))
            .andExpect(jsonPath("$.errors.password")
                .value("Password must be between 8 and 72 characters"));
    }

    @Test
    void loginSucceedsWithCorrectCredentials() throws Exception {
        registerAndGetToken("login@example.com");
        postJson("/api/auth/login", credentials("login@example.com", PASSWORD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void loginFailsWithSameMessageForWrongPasswordAndUnknownUser() throws Exception {
        registerAndGetToken("known@example.com");
        postJson("/api/auth/login", credentials("known@example.com", "wrong-password"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        postJson("/api/auth/login", credentials("ghost@example.com", "whatever-password"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenAccessesCurrentUser() throws Exception {
        String token = registerAndGetToken("me@example.com");
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("me@example.com"));
    }

    @Test
    void malformedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer not.a.jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = registerAndGetToken("tamper@example.com");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tampered))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = registerAndGetToken("expired@example.com");
        JsonNode me = objectMapper.readTree(mockMvc.perform(
                get("/api/users/me").header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getContentAsString());
        String expired = forgeToken(jwtSigningKey, me.get("id").asText(),
            jwtProperties.issuer(), Instant.now().minus(5, ChronoUnit.MINUTES));
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expired))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() throws Exception {
        SecretKey otherKey = new javax.crypto.spec.SecretKeySpec(
            "a-completely-different-32-byte-key!!".getBytes(), "HmacSHA256");
        String forged = forgeToken(otherKey, java.util.UUID.randomUUID().toString(),
            jwtProperties.issuer(), Instant.now().plus(5, ChronoUnit.MINUTES));
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + forged))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithWrongIssuerIsRejected() throws Exception {
        String forged = forgeToken(jwtSigningKey, java.util.UUID.randomUUID().toString(),
            "someone-else", Instant.now().plus(5, ChronoUnit.MINUTES));
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + forged))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUserCannotAccessAdminEndpoints() throws Exception {
        String token = registerAndGetToken("plain@example.com");
        mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAdminEndpoints() throws Exception {
        users.save(new User("admin@example.com", passwordEncoder.encode(PASSWORD), Role.ADMIN));
        String body = postJson("/api/auth/login", credentials("admin@example.com", PASSWORD))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("token").asText();
        mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.users").value(1));
    }
}
