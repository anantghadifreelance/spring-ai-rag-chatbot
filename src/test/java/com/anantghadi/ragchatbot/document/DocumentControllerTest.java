package com.anantghadi.ragchatbot.document;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    private static final IngestedDocument FAQ = new IngestedDocument("doc-1", "faq.md", "text/markdown", 2048, 2,
            List.of("chunk-a", "chunk-b"), Instant.parse("2026-11-01T10:15:30Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentIngestionService ingestionService;

    @Test
    void uploadReturnsCreatedWithLocationAndDocument() throws Exception {
        when(ingestionService.ingest(any(MultipartFile.class))).thenReturn(FAQ);

        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "faq.md", "text/markdown", "# FAQ".getBytes())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/documents/doc-1")))
                .andExpect(jsonPath("$.id").value("doc-1"))
                .andExpect(jsonPath("$.filename").value("faq.md"))
                .andExpect(jsonPath("$.chunkCount").value(2))
                .andExpect(jsonPath("$.chunkIds[0]").value("chunk-a"));
    }

    @Test
    void uploadWithoutFilePartIsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("other", "faq.md", "text/markdown", "x".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    void uploadOfUnsupportedTypeIsUnsupportedMediaTypeProblem() throws Exception {
        when(ingestionService.ingest(any(MultipartFile.class)))
                .thenThrow(new UnsupportedDocumentTypeException("photo.png"));

        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", new byte[] { 1 })))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Unsupported document type"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("photo.png")));
    }

    @Test
    void unreadableDocumentIsUnprocessableEntity() throws Exception {
        when(ingestionService.ingest(any(MultipartFile.class)))
                .thenThrow(new DocumentProcessingException("No extractable text found in 'scan.pdf'"));

        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "scan.pdf", "application/pdf", new byte[] { 1 })))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void listReturnsIngestedDocuments() throws Exception {
        when(ingestionService.findAll()).thenReturn(List.of(FAQ));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].filename").value("faq.md"))
                .andExpect(jsonPath("$[0].ingestedAt").value("2026-11-01T10:15:30Z"));
    }

    @Test
    void getUnknownDocumentIsNotFoundProblem() throws Exception {
        when(ingestionService.findById("nope")).thenThrow(new DocumentNotFoundException("nope"));

        mockMvc.perform(get("/api/documents/nope"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Document not found"))
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/document-not-found")));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/documents/doc-1")).andExpect(status().isNoContent());

        verify(ingestionService).delete("doc-1");
    }

    @Test
    void deleteUnknownDocumentIsNotFound() throws Exception {
        doThrow(new DocumentNotFoundException("missing")).when(ingestionService).delete("missing");

        mockMvc.perform(delete("/api/documents/missing")).andExpect(status().isNotFound());
    }
}
