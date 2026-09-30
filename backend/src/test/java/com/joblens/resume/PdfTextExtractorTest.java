package com.joblens.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joblens.support.TestPdf;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void extractsText() {
        String text = extractor.extract(TestPdf.fromLines(TestPdf.SAMPLE_RESUME));
        assertThat(text).contains("Jane Developer").contains("Spring Boot");
    }

    @Test
    void rejectsPdfWithoutText() {
        assertThatThrownBy(() -> extractor.extract(TestPdf.blankPage()))
            .isInstanceOf(UnreadableResumeException.class)
            .hasMessageContaining("No text found");
    }

    @Test
    void rejectsCorruptFile() {
        assertThatThrownBy(() -> extractor.extract("%PDF-1.7 not really".getBytes(StandardCharsets.US_ASCII)))
            .isInstanceOf(UnreadableResumeException.class);
    }

    @Test
    void rejectsEncryptedPdf() {
        assertThatThrownBy(() -> extractor.extract(TestPdf.encrypted()))
            .isInstanceOf(UnreadableResumeException.class);
    }

    @Test
    void rejectsTooManyPages() {
        assertThatThrownBy(() -> extractor.extract(TestPdf.pages(PdfTextExtractor.MAX_PAGES + 1)))
            .isInstanceOf(UnreadableResumeException.class)
            .hasMessageContaining("too long");
    }
}
