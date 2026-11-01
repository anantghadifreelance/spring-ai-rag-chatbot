package com.anantghadi.ragchatbot.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentTypeTest {

    @ParameterizedTest
    @CsvSource({ "notes.txt,TEXT", "README.md,MARKDOWN", "guide.MARKDOWN,MARKDOWN", "Report.PDF,PDF",
            "archive.v2.pdf,PDF" })
    void resolvesSupportedExtensionsCaseInsensitively(String filename, DocumentType expected) {
        assertThat(DocumentType.fromFilename(filename)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "image.png", "no-extension", "trailing-dot.", "slides.pptx" })
    void rejectsUnsupportedOrMissingExtensions(String filename) {
        assertThat(DocumentType.fromFilename(filename)).isEmpty();
    }
}
