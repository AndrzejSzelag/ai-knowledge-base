package pl.szelag.ai_knowledge_base.repository;

import java.util.UUID;

/** Projection for simplified document view (id + content + source). */
public interface DocumentProjection {
    UUID getId();
    String getContent();
    String getSource(); // mapped from "source" alias in native query
}