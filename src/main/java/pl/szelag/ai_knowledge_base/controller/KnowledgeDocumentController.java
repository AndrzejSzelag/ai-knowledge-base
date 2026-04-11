package pl.szelag.ai_knowledge_base.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.szelag.ai_knowledge_base.dto.DocumentDto;
import pl.szelag.ai_knowledge_base.service.KnowledgeIngestService;

/**
 * Exposes read-only endpoints for browsing ingested knowledge base documents.
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/documents")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeIngestService knowledgeIngestService;

    /** Returns a paginated list of ingested documents. */
    @GetMapping
    public Page<DocumentDto> getDocuments(Pageable pageable) {
        return knowledgeIngestService.getDocuments(pageable);
    }

    /** Returns total count of stored document chunks. */
    @GetMapping("/count")
    public long getCount() {
        return knowledgeIngestService.getCount();
    }
}
