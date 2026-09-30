package com.joblens.resume.dto;

import com.joblens.resume.Resume;
import java.time.Instant;
import java.util.UUID;

public record ResumeSummary(UUID id, String fileName, int version, long sizeBytes, Instant createdAt) {

    public static ResumeSummary from(Resume resume) {
        return new ResumeSummary(resume.getId(), resume.getFileName(), resume.getVersion(),
            resume.getSizeBytes(), resume.getCreatedAt());
    }
}
