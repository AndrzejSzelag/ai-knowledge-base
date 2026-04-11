#!/usr/bin/env python3
# ============================================================
# RAG Benchmark — Spring AI / Ollama / PGVector
# ============================================================
# Prerequisites before running:
#   1. Start Docker containers:
#        docker compose up -d
#   2. Ensure Ollama is running
#   3. Activate virtual environment and run:
#        python3 -m venv .venv
#        source .venv/bin/activate
#        pip install requests psycopg2-binary numpy
#        python3 benchmark.py
#        deactivate
# ============================================================
import sys
import time
import json

# --- DEPENDENCY CHECK ---
def check_dependencies():
    required = {'requests': 'requests', 'psycopg2': 'psycopg2-binary', 'numpy': 'numpy'}
    missing = []
    for module, package in required.items():
        try:
            __import__(module)
        except ImportError:
            missing.append(package)
    if missing:
        print(f"[!] Missing dependencies: {', '.join(missing)}")
        print(f"    Run: pip install {' '.join(missing)}")
        sys.exit(1)

check_dependencies()

import requests
import psycopg2
import numpy as np

# ============================================================
# CONFIGURATION (Synchronized with application.yml)
# ============================================================
DB_CONFIG = {
    "dbname": "ai-db",
    "user": "user",
    "password": "pass",
    "host": "localhost",
    "port": 5433  # Matches spring.datasource.url
}
OLLAMA_BASE_URL = "http://localhost:11434"
MODEL_NAME = "llama3.2:3b"        # ai.ollama.chat.model
EMBED_MODEL = "mxbai-embed-large"  # ai.ollama.embedding.model
EMBEDDING_DIM = 1024               # vectorstore.pgvector.dimensions

def test_pgvector_latency():
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        cur = conn.cursor()
        random_vector = np.random.rand(EMBEDDING_DIM).tolist()

        start_time = time.time()
        # Simulated RAG query using Cosine Distance (<=>)
        cur.execute("""
            SELECT id, content
            FROM vector_store
            ORDER BY embedding <=> %s::vector
            LIMIT 5;
        """, (random_vector,))

        results = cur.fetchall()
        latency = time.time() - start_time

        cur.close()
        conn.close()
        return {"latency": latency, "count": len(results)}
    except Exception as e:
        return {"error": str(e)}

def test_ollama_embeddings(text="Sample query for the knowledge base"):
    payload = {"model": EMBED_MODEL, "prompt": text}
    start_time = time.time()
    try:
        res = requests.post(f"{OLLAMA_BASE_URL}/api/embeddings", json=payload, timeout=30)
        if res.status_code == 200:
            return {"latency": time.time() - start_time}
        return {"error": f"Ollama Status: {res.status_code}"}
    except Exception as e:
        return {"error": str(e)}

def test_ollama_detailed(prompt="Briefly explain vector search.", ctx_size=1536):
    payload = {
        "model": MODEL_NAME,
        "prompt": prompt,
        "stream": True,
        "options": {
            "temperature": 0.2,   # ai.ollama.chat.options.temperature
            "num_ctx": ctx_size,  # ai.ollama.chat.options.num-ctx
            "num_thread": 4,      # ai.ollama.chat.options.num-thread
            "num_predict": 512    # ai.ollama.chat.options.num-predict
        }
    }

    try:
        start_time = time.time()
        response = requests.post(f"{OLLAMA_BASE_URL}/api/generate", json=payload, stream=True, timeout=120)

        ttft = None
        full_response_data = None

        for line in response.iter_lines():
            if line:
                chunk = json.loads(line)
                if ttft is None:
                    ttft = time.time() - start_time
                if chunk.get("done"):
                    full_response_data = chunk

        if full_response_data:
            # eval_duration is in nanoseconds in Ollama API
            eval_dur = full_response_data.get("eval_duration", 0) / 1e9
            eval_count = full_response_data.get("eval_count", 0)
            total_duration = full_response_data.get("total_duration", 0) / 1e9

            return {
                "ttft": ttft,
                "total_duration": total_duration,
                "load_duration": full_response_data.get("load_duration", 0) / 1e9,
                "prompt_eval_count": full_response_data.get("prompt_eval_count"),
                "eval_count": eval_count,
                "tps": eval_count / eval_dur if eval_dur > 0 else 0
            }
        return {"error": "No response data received"}
    except Exception as e:
        return {"error": str(e)}

def print_bar(label, percentage):
    bar_len = 25
    filled_len = int(round(bar_len * percentage / 100))
    bar = '█' * filled_len + '░' * (bar_len - filled_len)
    print(f"    {label.ljust(12)}: [{bar}] {percentage:.1f}%")

if __name__ == "__main__":
    print("=" * 60)
    print("      SPRING AI / OLLAMA / PGVECTOR RAG BENCHMARK")
    print("=" * 60)

    print("\n--- WARM-UP (Loading models) ---")
    warmup_emb = test_ollama_embeddings("warmup")
    warmup_ol = test_ollama_detailed("hi")

    if "error" in warmup_emb or "error" in warmup_ol:
        print("[!] Warm-up failed — Services might be down.")
        if "error" in warmup_emb: print(f"    Embedding Error: {warmup_emb['error']}")
        if "error" in warmup_ol: print(f"    LLM Error: {warmup_ol['error']}")
        sys.exit(1)
    print("[+] Models ready.\n")

    # Perform actual measurements
    emb = test_ollama_embeddings()
    pg = test_pgvector_latency()
    ol = test_ollama_detailed()

    if "error" not in emb and "error" not in pg and "error" not in ol:
        total_e2e = emb['latency'] + pg['latency'] + ol['total_duration']
        ux_latency = emb['latency'] + pg['latency'] + ol['ttft']

        print(f"RESULTS FOR {MODEL_NAME} + {EMBED_MODEL}")
        print("-" * 60)
        print(f"[1] Embedding Latency:    {emb['latency']:.4f}s")
        print(f"[2] PGVector Latency:     {pg['latency']:.4f}s")
        print(f"[3] LLM Generation:       {ol['total_duration']:.4f}s")
        print(f"    -> Time to 1st Token: {ol['ttft']:.4f}s")
        print(f"    -> Speed:             {ol['tps']:.2f} tokens/s")
        print("-" * 60)

        print("TIME DISTRIBUTION (End-to-End):")
        print_bar("Embedding",  (emb['latency']        / total_e2e) * 100)
        print_bar("DB Search",  (pg['latency']          / total_e2e) * 100)
        print_bar("Generation", (ol['total_duration']   / total_e2e) * 100)

        print("-" * 60)
        print(f"UX LATENCY (Start -> 1st Token): {ux_latency:.4f}s")
        print(f"TOTAL SYSTEM E2E TIME:           {total_e2e:.4f}s")
        print("=" * 60)
    else:
        print("[!] Benchmark failed during execution.")