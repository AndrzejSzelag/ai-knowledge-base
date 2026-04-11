# Benchmarking

A Python benchmark script measures end-to-end RAG pipeline latency across all three stages: embedding → vector search → LLM generation.

**Prerequisites:**
- Docker containers running: `docker compose up -d`
- Ollama running: `ollama serve`

**Setup and run:**

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install requests psycopg2-binary numpy
python3 benchmark.py

# Optional: Run this when you are finished working in this environment
deactivate
```

**What it measures:**

- Embedding latency (mxbai-embed-large via Ollama)
- PGVector similarity search latency (HNSW)
- LLM generation time and tokens per second (TPS)
- UX latency (time to first token)
- Total E2E time with percentage distribution

> **Note:** The app runs an AI warm-up sequence on startup to pre-load LLM models into memory, reducing first-response latency.

## Example Output

Model tested: `llama3.2:3b + mxbai-embed-large`

| Metric                  | Mac (Apple Silicon) | Windows (Intel i5 CPU-only) |
| ----------------------- | :-----------------: | :-------------------------: |
| Embedding Latency       | 0.35 s              | 2.14 s                      |
| PGVector Latency        | 0.02 s              | 0.02 s                      |
| LLM Generation          | 16.96 s             | 33.55 s                     |
| Time to 1st Token       | 0.66 s              | 5.51 s                      |
| Tokens per second (TPS) | 14.53               | 7.47                        |
| Total E2E Time          | 17.33 s             | 35.71 s                     |

## Performance Analysis (CPU-only Inference)

The system was benchmarked on a standard business-class laptop (**Intel i5 Low Power, 24GB RAM**) to evaluate real-world local RAG performance without a dedicated GPU.

| Stage               | Mac Latency | Windows Latency | Share | Analysis |
| :------------------ | :---------: | :-------------: | :---- | :------- |
| **Embedding**       | 0.35s | 2.14s | 2–6%  | Fast on Apple Silicon due to hardware acceleration (ANE / Metal). Moderate on CPU-only Intel i5. |
| **PGVector Search** | 0.02s | 0.02s | 0%    | **Negligible.** HNSW is extremely efficient even on low-end hardware. |
| **LLM Generation**  | 16.96s | 33.55s | 94%  | **Bottleneck.** CPU-bound matrix multiplication limits throughput. Time to first token varies dramatically. |
| **UX Latency**      | 0.66s | 5.51s | —     | Streaming (SSE) reduces perceived latency. Users see output in seconds despite long E2E time. |
| **Total E2E**       | 17.33s | 35.71s | 100% | Mac ~2× faster than Intel CPU-only system. |

**Key Takeaways**
1. **Vector Search is negligible** - even on low-end hardware, pgvector scaling is not an issue.
2. **LLM Generation is the main limit** - switching to a 1B model or using prompt caching could reduce CPU bottleneck.
3. **UX Strategy** - Streaming (SSE) improves perceived interactivity: first token arrives within seconds.
4. **Platform differences matter** - Apple Silicon benefits from hardware acceleration, while CPU-only Intel machines see a 2–6× slowdown.

---

← [Back to README](../README.md)