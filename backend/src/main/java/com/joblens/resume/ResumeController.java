package com.joblens.resume;

import com.joblens.resume.dto.ParsedResumeRequest;
import com.joblens.resume.dto.ResumeDetail;
import com.joblens.resume.dto.ResumeSummary;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @GetMapping
    public List<ResumeSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return resumeService.list(userId(jwt));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeDetail upload(@AuthenticationPrincipal Jwt jwt,
                               @RequestPart("file") MultipartFile file) {
        return resumeService.upload(userId(jwt), file);
    }

    @GetMapping("/{id}")
    public ResumeDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return resumeService.get(userId(jwt), id);
    }

    @PutMapping("/{id}/parsed")
    public ResumeDetail updateParsed(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody ParsedResumeRequest request) {
        return resumeService.updateParsed(userId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        resumeService.delete(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
