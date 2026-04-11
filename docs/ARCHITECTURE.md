# Architecture

Full system architecture for the AI Knowledge Base RAG application.

## System Diagram

### Overview (Text)
```text
React (Vite SPA)
    │  HTTP (REST) + SSE + CSRF cookie
    ▼
Spring Boot
    ├── Filters & Security
    │   ├── RateLimitFilter              — 20 req/min per IP (Bucket4j)
    │   └── RestAuthenticationEntryPoint — 401 for /api/*, redirect for web
    │
    ├── Controllers
    │   ├── AuthController
    │   │       GET  /api/auth/me
    │   │
    │   ├── KnowledgeIngestController
    │   │       POST   /api/ai/ingest
    │   │       PUT    /api/ai/ingest/{id}
    │   │       DELETE /api/ai/ingest/{id}
    │
    ├── KnowledgeDocumentController
    │   │       GET    /api/ai/documents
    │   │       GET    /api/ai/documents/count
    │   │
    │   └── KnowledgeQueryController
    │           POST   /api/ai/ask
    │           POST   /api/ai/ask-streaming (SSE)
    │
    ├── Services
    │   ├── KnowledgeIngestService
    │   │       TokenTextSplitter(dynamic via app.splitter)
    │   │       → VectorStore.add()
    │   │       → cache eviction on updates
    │   │
    │   └── KnowledgeQueryService (RAG pipeline)
    │           question
    │             → embedding
    │             → similaritySearch(topK=3, threshold=0.60)
    │             → context truncation (max 3000 chars)
    │             → prompt (system + user)
    │             → ChatModel
    │             → answer + sources
    │
    ▼
Data & Models

pgvector (PostgreSQL 16)        Ollama (local runtime)
  ├── table:      vector_store      ┌──────────────────────────────────────┐
  ├── index:      HNSW              │  mxbai-embed-large  (embeddings)     │
  ├── distance:   cosine            │  llama3.2:3B        (chat model)     │
  └── dimensions: 1024              └──────────────────────────────────────┘
```

### Component Diagram (Interactive)

```mermaid
graph TD
    %% Global styling - poprawione dla lepszego kontrastu
    classDef plain fill:#ffffff,stroke:#333,stroke-width:2px,color:#000;
    classDef db fill:#b3e5fc,stroke:#01579b,stroke-width:2px,color:#000;
    classDef ai fill:#e1bee7,stroke:#4a148c,stroke-width:2px,color:#000;
    classDef tech fill:#f9fbe7,stroke:#33691e,stroke-width:1px,stroke-dasharray: 5 5,color:#000;
    classDef green fill:#c8e6c9,stroke:#1b5e20,stroke-width:2px,color:#000;
    classDef orange fill:#ffe0b2,stroke:#e65100,stroke-width:2px,color:#000;
    classDef blue fill:#bbdefb,stroke:#0d47a1,stroke-width:2px,color:#000;
    classDef main fill:#eceff1,stroke:#263238,stroke-width:3px,color:#000;

    %% Components
    Frontend["React - Vite SPA"]:::main

    subgraph SpringBoot [Spring Boot Backend]
        direction TB
        
        subgraph NetLayer [Communication Layer]
            direction LR
            Proto[HTTP REST]:::plain
            SSE[SSE Stream]:::plain
            CSRF[CSRF Cookie]:::plain
        end

        subgraph Security [Filters and Security]
            direction TB
            RateLimit["RateLimitFilter - Bucket4j"]:::orange
            AuthPoint["RestAuthEntryPoint - 401/Redirect"]:::orange
        end

        subgraph Controllers [Controllers]
            direction TB
            
            subgraph AuthCtrl [AuthController]
                Me["GET /api/auth/me"]:::green
            end
            
            subgraph IngestCtrl [KnowledgeIngestController]
                Ingest["POST /api/ai/ingest"]:::orange
                Update["PUT /api/ai/ingest/{id}"]:::orange
                Delete["DELETE /api/ai/ingest/{id}"]:::orange
            end
            
            subgraph DocCtrl [KnowledgeDocumentController]
                GetDocs["GET /api/ai/documents"]:::green
                CountDocs["GET /api/ai/documents/count"]:::green
            end
            
            subgraph QueryCtrl [KnowledgeQueryController]
                Ask["POST /api/ai/ask"]:::blue
                AskStream["POST /api/ai/ask-streaming"]:::blue
            end
        end

        subgraph Services [Services]
            direction TB
            
            subgraph IngestServ [KnowledgeIngestService]
                Split[TokenTextSplitter]:::tech
                VStoreAdd["VectorStore.add()"]:::tech
                CacheEvict[Cache Eviction]:::tech
            end
            
            subgraph QueryServ [KnowledgeQueryService - RAG]
                EmbedQuest["1. Question - Embedding"]:::tech
                SimSearch["2. similaritySearch topK=3"]:::tech
                Truncate["3. Context Truncation"]:::tech
                PromptBuilder["4. Prompt Construction"]:::tech
                Chat["5. ChatModel - Ollama"]:::tech
                Answer["6. Answer + Sources"]:::tech
            end
        end
    end

    subgraph DataLayer [Data and Models]
        direction LR
        
        subgraph Postgres [pgvector - PostgreSQL 16]
            direction TB
            Table["table: vector_store"]:::db
            Index["index: HNSW"]:::db
            Dist["distance: cosine"]:::db
            Dim["dimensions: 1024"]:::db
        end
        
        subgraph Ollama [Ollama - local runtime]
            direction TB
            ModelEmbed["mxbai-embed-large - embeddings"]:::ai
            ModelChat["llama3.2:3B - chat model"]:::ai
        end
    end

    %% Flow/Connections
    Frontend --> NetLayer
    NetLayer ==> Security
    Security --> Controllers
    
    IngestCtrl --> IngestServ
    Update --> CacheEvict
    Delete --> CacheEvict
    QueryCtrl --> QueryServ

    IngestServ -.-> VStoreAdd
    QueryServ -.-> SimSearch
    
    VStoreAdd ==> Postgres
    SimSearch ==> Postgres
    
    EmbedQuest ==> ModelEmbed
    Chat ==> ModelChat
```

**Separation of concerns:**

- React handles presentation only
- Spring Boot owns all business logic, vectorisation and LLM communication
- pgvector stores and indexes embeddings (HNSW index + GIN on metadata)
- Ollama runs both models locally — no cloud dependency

## Tech Stack

| Layer           | Technology                                                        |
| --------------- | ----------------------------------------------------------------- |
| Frontend        | React 19, Vite                                                    |
| Backend         | Java 21, Spring Boot 3.4.1, Spring AI 1.0-M5                      |
| Vector DB       | PostgreSQL 16 + pgvector (HNSW index, cosine distance, 1024dim)   |
| Embedding model | mxbai-embed-large via Ollama (1024 dimensions, 512 token limit)   |
| Chat model      | llama3.2:3B via Ollama                                            |
| Auth            | Spring Security + Google OAuth2 (session-based)                   |
| Rate limiting   | Bucket4j (token bucket, per IP)                                   |
| Caching         | Spring Cache (`documentCount`, `documents`)                       |
| Infrastructure  | Docker Compose                                                    |

---

← [Back to README](../README.md)
