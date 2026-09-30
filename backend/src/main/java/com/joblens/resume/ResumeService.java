package com.joblens.resume;

import com.joblens.common.error.ConflictException;
import com.joblens.common.error.NotFoundException;
import com.joblens.common.storage.FileStorage;
import com.joblens.resume.dto.ParsedResumeRequest;
import com.joblens.resume.dto.ResumeDetail;
import com.joblens.resume.dto.ResumeSummary;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Resume lifecycle. Every lookup is scoped by user id, so other users' resumes read as missing. */
@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private final ResumeRepository resumes;
    private final ResumeUploadValidator validator;
    private final PdfTextExtractor extractor;
    private final ResumeParser parser;
    private final FileStorage storage;
    private final JsonMapper json;

    public ResumeService(ResumeRepository resumes, ResumeUploadValidator validator,
                         PdfTextExtractor extractor, ResumeParser parser,
                         FileStorage storage, JsonMapper json) {
        this.resumes = resumes;
        this.validator = validator;
        this.extractor = extractor;
        this.parser = parser;
        this.storage = storage;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<ResumeSummary> list(UUID userId) {
        return resumes.findByUserIdOrderByVersionDesc(userId).stream().map(ResumeSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public ResumeDetail get(UUID userId, UUID id) {
        Resume resume = find(userId, id);
        return ResumeDetail.from(resume, read(resume));
    }

    /** The user's newest resume version, if they have uploaded one. */
    @Transactional(readOnly = true)
    public java.util.Optional<ResumeDetail> findLatest(UUID userId) {
        return resumes.findFirstByUserIdOrderByVersionDesc(userId)
            .map(resume -> ResumeDetail.from(resume, read(resume)));
    }

    @Transactional
    public ResumeDetail upload(UUID userId, MultipartFile file) {
        byte[] content = validator.validate(file);
        String text = extractor.extract(content);
        ParsedResume parsed = parser.parse(text);

        String storageKey = UUID.randomUUID() + ".pdf";
        storage.store(storageKey, content);
        try {
            Resume resume = new Resume(userId, validator.safeFileName(file.getOriginalFilename()),
                "application/pdf", content.length, storageKey, text, write(parsed),
                resumes.findMaxVersion(userId) + 1);
            Resume saved = resumes.saveAndFlush(resume);
            log.info("Resume uploaded: id={} version={} bytes={}", saved.getId(), saved.getVersion(),
                saved.getSizeBytes());
            return ResumeDetail.from(saved, parsed);
        } catch (DataIntegrityViolationException e) {
            storage.delete(storageKey);
            throw new ConflictException("Another upload was in progress. Please try again");
        } catch (RuntimeException e) {
            storage.delete(storageKey);
            throw e;
        }
    }

    @Transactional
    public ResumeDetail updateParsed(UUID userId, UUID id, ParsedResumeRequest request) {
        Resume resume = find(userId, id);
        ParsedResume parsed = new ParsedResume(blankToNull(request.name()), blankToNull(request.email()),
            blankToNull(request.phone()), blankToNull(request.summary()), clean(request.skills()),
            clean(request.experience()), clean(request.education()), clean(request.projects()),
            clean(request.certifications()));
        resume.setParsedData(write(parsed));
        return ResumeDetail.from(resumes.saveAndFlush(resume), parsed);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Resume resume = find(userId, id);
        resumes.delete(resume);
        resumes.flush();
        try {
            storage.delete(resume.getStorageKey());
        } catch (RuntimeException e) {
            // The row is already gone; an orphaned file is harmless but worth a log line.
            log.warn("Could not delete stored file for resume {}", id, e);
        }
        log.info("Resume deleted: id={}", id);
    }

    private Resume find(UUID userId, UUID id) {
        return resumes.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new NotFoundException("Resume not found"));
    }

    private ParsedResume read(Resume resume) {
        try {
            return json.readValue(resume.getParsedData(), ParsedResume.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Stored resume data is corrupt for " + resume.getId(), e);
        }
    }

    private String write(ParsedResume parsed) {
        try {
            return json.writeValueAsString(parsed);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialise parsed resume", e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static List<String> clean(List<String> values) {
        return values == null ? List.of()
            : values.stream().filter(v -> v != null && !v.isBlank()).map(String::strip).toList();
    }
}
