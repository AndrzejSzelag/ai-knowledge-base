#!/bin/bash
# ==============================================================================
# AI Knowledge Base — Integration Test Runner
# ==============================================================================
# This script automates the lifecycle of the test environment:
#   1. Orchestrates a dedicated PostgreSQL + pgvector container.
#   2. Waits for the database to be fully operational.
#   3. Executes Maven 'verify' (Unit & Integration tests).
#
# Requirements:
#   - Docker Desktop running (daemon must be active)
#   - Maven installed and available in PATH (mvn command works)
#   - Bash environment (Linux / WSL / Git Bash on Windows)
#
# Notes:
#   - This script will create the test container if it does not exist.
#   - No need to run 'docker compose up' beforehand.
#   - Credentials below are for local testing only. Do NOT use in CI/CD
#     without proper secrets management.
#
# Usage:
#   chmod +x test-it.sh && ./test-it.sh
# ==============================================================================

# Exit immediately if a command exits with a non-zero status
set -e

# --- Check prerequisites ---
command -v docker >/dev/null 2>&1 || { echo "Docker not installed or not in PATH."; exit 1; }
command -v mvn    >/dev/null 2>&1 || { echo "Maven not installed or not in PATH.";  exit 1; }

# --- Configuration ---
# WARNING: For local testing only — do not commit real credentials.
CONTAINER_NAME="ai-db-test"
DB_PORT=5555
DB_NAME="ai-db-test"
DB_USER="user"
DB_PASS="pass"
IMAGE="pgvector/pgvector:pg16"
DB_READY_TIMEOUT=30

echo "------------------------------------------------------------"
echo "  STEP 1: Starting test database container..."
echo "------------------------------------------------------------"

if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    docker start "$CONTAINER_NAME" > /dev/null
    echo "[+] Existing container '${CONTAINER_NAME}' started."
else
    docker run -d \
        --name "$CONTAINER_NAME" \
        -p "${DB_PORT}:5432" \
        -e "POSTGRES_DB=$DB_NAME" \
        -e "POSTGRES_USER=$DB_USER" \
        -e "POSTGRES_PASSWORD=$DB_PASS" \
        "$IMAGE" > /dev/null
    echo "[+] New container '${CONTAINER_NAME}' created and started."
fi

echo -n "  Waiting for database at localhost:${DB_PORT} to be ready..."

retries=$DB_READY_TIMEOUT
until docker exec "$CONTAINER_NAME" pg_isready -U "$DB_USER" -d "$DB_NAME" > /dev/null 2>&1; do
    if [ "$retries" -le 0 ]; then
        echo -e "\n[!] Timeout: database did not become ready within ${DB_READY_TIMEOUT}s."
        echo "    Check 'docker logs ${CONTAINER_NAME}' for details."
        exit 1
    fi
    retries=$((retries - 1))
    echo -n "."
    sleep 1
done

echo -e "\n[+] Database is READY."

echo -e "\n------------------------------------------------------------"
echo "  STEP 2: Executing Maven Lifecycle (verify)"
echo "------------------------------------------------------------"

mvn verify

echo -e "\n------------------------------------------------------------"
echo "  TEST RUN COMPLETED SUCCESSFULLY"
echo "------------------------------------------------------------"

# Optional cleanup — uncomment to stop the container after tests:
# docker stop "$CONTAINER_NAME"

echo "Done."