package pl.szelag.ai_knowledge_base.controller;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.szelag.ai_knowledge_base.dto.IngestRequest;
import pl.szelag.ai_knowledge_base.service.KnowledgeIngestService;

/** Handles document ingestion, updates, and deletion in the vector store. */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Validated
public class KnowledgeIngestController {

    private final KnowledgeIngestService knowledgeIngestService;

    /** Ingests text and stores its vector embedding. */
    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public void ingest(@RequestBody @Valid IngestRequest request) {
        log.info("Ingesting document: length={}", request.text().length());
        knowledgeIngestService.ingest(request.text());
    }

    /** Updates an existing document by re-ingesting its content. */
    @PutMapping("/ingest/{documentId}")
    @ResponseStatus(HttpStatus.OK)
    public void update(@PathVariable String documentId, @RequestBody @Valid IngestRequest request) {
        log.info("Updating document: id={}, length={}", documentId, request.text().length());
        knowledgeIngestService.updateDocument(documentId, request.text());
    }

    /** Deletes a document by ID. */
    @DeleteMapping("/ingest/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String documentId) {
        log.info("Deleting document: id={}", documentId);
        knowledgeIngestService.deleteDocument(documentId);
    }
}