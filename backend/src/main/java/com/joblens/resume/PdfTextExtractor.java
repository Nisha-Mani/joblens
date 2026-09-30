package com.joblens.resume;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor {

    static final int MAX_PAGES = 10;
    static final int MAX_TEXT_LENGTH = 100_000;

    /** @throws UnreadableResumeException if the file is not a readable, unencrypted PDF */
    public String extract(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.isEncrypted()) {
                throw new UnreadableResumeException("Password-protected PDFs are not supported");
            }
            if (document.getNumberOfPages() > MAX_PAGES) {
                throw new UnreadableResumeException(
                    "Resume is too long (maximum " + MAX_PAGES + " pages)");
            }
            String text = new PDFTextStripper().getText(document);
            // PostgreSQL text columns cannot store NUL characters.
            String cleaned = text.replace("\u0000", "").strip();
            if (cleaned.isEmpty()) {
                throw new UnreadableResumeException(
                    "No text found in this PDF. Scanned or image-only resumes are not supported yet");
            }
            return cleaned.length() > MAX_TEXT_LENGTH ? cleaned.substring(0, MAX_TEXT_LENGTH) : cleaned;
        } catch (IOException e) {
            throw new UnreadableResumeException("Could not read this file as a PDF");
        }
    }
}
