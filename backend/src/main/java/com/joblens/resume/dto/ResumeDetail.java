package com.joblens.resume.dto;

import com.joblens.resume.ParsedResume;
import com.joblens.resume.Resume;
import java.time.Instant;
import java.util.UUID;

public record ResumeDetail(UUID id, String fileName, int version, long sizeBytes,
                           int extractedTextLength, Instant createdAt, Instant updatedAt,
                           ParsedResume parsed) {

    public static ResumeDetail from(Resume resume, ParsedResume parsed) {
        return new ResumeDetail(resume.getId(), resume.getFileName(), resume.getVersion(),
            resume.getSizeBytes(), resume.getExtractedText().length(), resume.getCreatedAt(),
            resume.getUpdatedAt(), parsed);
    }
}
