package com.anantghadi.ragchatbot.web;

import com.anantghadi.ragchatbot.document.DocumentNotFoundException;
import com.anantghadi.ragchatbot.document.DocumentProcessingException;
import com.anantghadi.ragchatbot.document.InvalidDocumentException;
import com.anantghadi.ragchatbot.document.UnsupportedDocumentTypeException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 9457 {@link ProblemDetail} responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://github.com/anantghadifreelance/spring-ai-rag-chatbot/problems/";

    @ExceptionHandler(DocumentNotFoundException.class)
    ProblemDetail handleNotFound(DocumentNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Document not found", "document-not-found", ex.getMessage());
    }

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    ProblemDetail handleUnsupportedType(UnsupportedDocumentTypeException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported document type", "unsupported-document-type",
                ex.getMessage());
    }

    @ExceptionHandler(InvalidDocumentException.class)
    ProblemDetail handleInvalidDocument(InvalidDocumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid document", "invalid-document", ex.getMessage());
    }

    @ExceptionHandler(DocumentProcessingException.class)
    ProblemDetail handleProcessing(DocumentProcessingException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Document could not be processed", "document-processing",
                ex.getMessage());
    }

    @ExceptionHandler({ NonTransientAiException.class, TransientAiException.class })
    ProblemDetail handleAiProvider(RuntimeException ex) {
        log.warn("AI provider call failed: {}", ex.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "AI provider error", "ai-provider-error",
                "The language model provider could not complete the request. Please retry later.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "internal-error",
                "An unexpected error occurred.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed", "validation-error",
                "Request validation failed");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", "file-too-large",
                "The uploaded file exceeds the maximum allowed size.");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String type, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_BASE + type));
        return problem;
    }
}
