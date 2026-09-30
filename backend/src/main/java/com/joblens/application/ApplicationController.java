package com.joblens.application;

import com.joblens.application.dto.ApplicationDetail;
import com.joblens.application.dto.ApplicationRequest;
import com.joblens.application.dto.ApplicationSummary;
import com.joblens.application.dto.StatusRequest;
import com.joblens.common.web.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ApplicationSummary> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) UUID jobId,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(userId(jwt), q, status, jobId, sortBy, direction, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationDetail create(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody ApplicationRequest request) {
        return service.create(userId(jwt), request);
    }

    @GetMapping("/{id}")
    public ApplicationDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(userId(jwt), id);
    }

    @PutMapping("/{id}")
    public ApplicationDetail update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                    @Valid @RequestBody ApplicationRequest request) {
        return service.update(userId(jwt), id, request);
    }

    @PatchMapping("/{id}/status")
    public ApplicationDetail changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(userId(jwt), id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
