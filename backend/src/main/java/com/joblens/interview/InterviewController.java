package com.joblens.interview;

import com.joblens.common.web.PageResponse;
import com.joblens.interview.dto.CustomQuestionRequest;
import com.joblens.interview.dto.GenerateRequest;
import com.joblens.interview.dto.PrepUpdateRequest;
import com.joblens.interview.dto.QuestionResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InterviewController {

    private final InterviewService service;

    public InterviewController(InterviewService service) {
        this.service = service;
    }

    @PostMapping("/jobs/{jobId}/interview-questions/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public List<QuestionResponse> generate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID jobId,
                                           @RequestBody(required = false) GenerateRequest request) {
        return service.generate(userId(jwt), jobId, request == null ? null : request.resumeId());
    }

    @GetMapping("/interviews/questions")
    public PageResponse<QuestionResponse> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) InterviewCategory category,
            @RequestParam(required = false) PrepStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.search(userId(jwt), jobId, category, status, page, size);
    }

    @PostMapping("/interviews/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionResponse addCustom(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody CustomQuestionRequest request) {
        return service.addCustom(userId(jwt), request);
    }

    @PutMapping("/interviews/questions/{id}")
    public QuestionResponse updatePrep(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                       @Valid @RequestBody PrepUpdateRequest request) {
        return service.updatePrep(userId(jwt), id, request);
    }

    @DeleteMapping("/interviews/questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
