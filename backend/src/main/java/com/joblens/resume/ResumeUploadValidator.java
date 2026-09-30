package com.joblens.resume;

import com.joblens.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Validates an upload before any parsing: presence, declared type, extension and real content. */
@Component
public class ResumeUploadValidator {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private final long maxBytes;
    private final long maxMb;

    public ResumeUploadValidator(@Value("${MAX_UPLOAD_MB:5}") long maxMb) {
        this.maxMb = maxMb;
        this.maxBytes = maxMb * 1024 * 1024;
    }

    public byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UnreadableResumeException("Choose a PDF file to upload");
        }
        // The servlet container enforces this too; checking here keeps the rule in one testable place.
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE,
                "The file is too large. Maximum size is " + maxMb + " MB.");
        }
        String name = file.getOriginalFilename();
        boolean pdfType = "application/pdf".equalsIgnoreCase(file.getContentType());
        boolean pdfExtension = name != null && name.toLowerCase().endsWith(".pdf");
        if (!pdfType || !pdfExtension) {
            throw new UnreadableResumeException("Only PDF files are supported");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (java.io.IOException e) {
            throw new UnreadableResumeException("Could not read the uploaded file");
        }
        // The declared type is client-controlled, so verify the actual file signature too.
        if (content.length < PDF_MAGIC.length
            || !Arrays.equals(Arrays.copyOf(content, PDF_MAGIC.length), PDF_MAGIC)) {
            throw new UnreadableResumeException("The file does not look like a valid PDF");
        }
        return content;
    }

    /** Strips any path and control characters from the client-supplied file name. */
    public String safeFileName(String original) {
        String base = original == null ? "resume.pdf" : original;
        base = base.substring(Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\')) + 1);
        base = base.replaceAll("[\\p{Cntrl}]", "").strip();
        if (base.isEmpty()) {
            base = "resume.pdf";
        }
        return base.length() > MAX_FILE_NAME_LENGTH
            ? base.substring(base.length() - MAX_FILE_NAME_LENGTH) : base;
    }
}
