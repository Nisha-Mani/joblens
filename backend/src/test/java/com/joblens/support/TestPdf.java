package com.joblens.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Builds small PDFs for tests. */
public final class TestPdf {

    public static final List<String> SAMPLE_RESUME = List.of(
        "Jane Developer",
        "jane.dev@example.com | +1 (415) 555-0134",
        "",
        "SUMMARY",
        "Full-stack engineer with 6 years of experience building web platforms.",
        "",
        "SKILLS",
        "Languages: Java, TypeScript, Python",
        "Frameworks: Spring Boot, React",
        "",
        "EXPERIENCE",
        "Senior Engineer, Acme Corp (2021 - Present)",
        "Built REST APIs with Spring Boot and PostgreSQL on AWS.",
        "",
        "EDUCATION",
        "B.S. Computer Science, State University (2018)",
        "",
        "CERTIFICATIONS",
        "AWS Certified Developer");

    private TestPdf() { }

    public static byte[] fromLines(List<String> lines) {
        return build(1, lines, false);
    }

    public static byte[] blankPage() {
        return build(1, List.of(), false);
    }

    public static byte[] pages(int count) {
        return build(count, List.of("Page content"), false);
    }

    public static byte[] encrypted() {
        return build(1, List.of("secret"), true);
    }

    private static byte[] build(int pages, List<String> lines, boolean encrypt) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int p = 0; p < pages; p++) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (lines.isEmpty()) {
                    continue;
                }
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    content.setLeading(14);
                    content.newLineAtOffset(50, 740);
                    for (String line : lines) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            if (encrypt) {
                StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner", "user", new AccessPermission());
                policy.setEncryptionKeyLength(128);
                document.protect(policy);
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
