package com.anantghadi.ragchatbot.document;

import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Upload, list and delete the documents that ground the chatbot's answers.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService ingestionService;

    public DocumentController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestPart("file") MultipartFile file) {
        IngestedDocument document = ingestionService.ingest(file);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(document.id())
                .toUri();
        return ResponseEntity.created(location).body(DocumentResponse.from(document));
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return ingestionService.findAll().stream().map(DocumentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable String id) {
        return DocumentResponse.from(ingestionService.findById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        ingestionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
