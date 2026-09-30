package com.joblens.hardening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Discovers every controller route and proves anonymous callers are rejected, so a new endpoint
 * added without security cannot ship unnoticed. The public list below is the complete, reviewed
 * set of routes that are allowed to work without a token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndpointSecurityTest {

    /** Method + path pattern of routes that are intentionally public. */
    private static final Set<String> PUBLIC = Set.of(
        "POST /api/auth/register",
        "POST /api/auth/login",
        "GET /api/ping");

    @Autowired MockMvc mockMvc;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    private record Route(HttpMethod method, String path) {
        String key() { return method + " " + path; }
    }

    private List<Route> apiRoutes() {
        List<Route> routes = new ArrayList<>();
        for (var entry : mapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();
            // Skip routes that come from the framework or tests rather than this application.
            if (!handler.getBeanType().getPackageName().startsWith("com.joblens")
                || handler.getBeanType().getName().contains("Test")) {
                continue;
            }
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                for (var method : info.getMethodsCondition().getMethods()) {
                    routes.add(new Route(HttpMethod.valueOf(method.name()), pattern));
                }
            }
        }
        return routes;
    }

    @Test
    void theApplicationExposesTheRoutesWeExpect() {
        // Guards the sweep itself: if route discovery silently broke, the next test would pass vacuously.
        assertThat(apiRoutes()).hasSizeGreaterThan(30);
    }

    @Test
    void everyNonPublicRouteRejectsAnonymousRequests() throws Exception {
        List<String> leaks = new ArrayList<>();
        for (Route route : apiRoutes()) {
            if (PUBLIC.contains(route.key())) {
                continue;
            }
            String concrete = route.path().replaceAll("\\{[^/}]+}", UUID.randomUUID().toString());
            int status = mockMvc.perform(request(route.method(), concrete)).andReturn().getResponse().getStatus();
            if (status != 401) {
                leaks.add(route.key() + " → " + status);
            }
        }
        assertThat(leaks).as("routes reachable without authentication").isEmpty();
    }

    @Test
    void publicRoutesAreExactlyTheReviewedList() {
        // If a new public route is added in SecurityConfig it must be added here deliberately.
        List<String> discovered = apiRoutes().stream().map(Route::key).toList();
        assertThat(discovered).containsAll(PUBLIC);
    }

    @Test
    void adminRoutesRejectOrdinaryUsers() throws Exception {
        for (Route route : apiRoutes()) {
            if (!route.path().startsWith("/api/admin")) {
                continue;
            }
            int status = mockMvc.perform(request(route.method(), route.path())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))))
                .andReturn().getResponse().getStatus();
            assertThat(status).as(route.key()).isEqualTo(403);
        }
    }

    @Test
    void unknownRoutesDoNotLeakExistenceToAnonymousCallers() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, "/api/definitely-not-a-route")).andExpect(status().isUnauthorized());
    }
}
