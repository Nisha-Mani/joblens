package com.joblens.analysis;

import com.joblens.analysis.dto.AnalysisResponse;
import com.joblens.analysis.dto.AnalysisSummary;
import com.joblens.analysis.dto.AnalyzeRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final ResumeAnalysisService service;

    public AnalysisController(ResumeAnalysisService service) {
        this.service = service;
    }

    @PostMapping("/jobs/{jobId}/analyze")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResponse analyze(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID jobId,
                                    @RequestBody(required = false) AnalyzeRequest request) {
        return service.analyze(userId(jwt), jobId, request == null ? null : request.resumeId());
    }

    @GetMapping("/jobs/{jobId}/analyses")
    public List<AnalysisSummary> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID jobId) {
        return service.listForJob(userId(jwt), jobId);
    }

    @GetMapping("/analyses/{id}")
    public AnalysisResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
