# ===================================================================
# AI Knowledge Base — Integration Test Runner (PowerShell)
# ===================================================================
# This script automates the lifecycle of the test environment:
#   1. Orchestrates a dedicated PostgreSQL + pgvector container.
#   2. Waits for the database to be fully operational.
#   3. Executes Maven 'verify' (Unit & Integration tests).
#
# Requirements:
#   - Docker Desktop running (daemon must be active)
#   - Maven installed and available in PATH
#   - PowerShell 7+ recommended
#
# Notes:
#   - Credentials below are for local testing only. Do NOT use in CI/CD
#     without proper secrets management.
#
# Usage:
#   .\test-it.ps1
# ===================================================================

# Exit on errors
$ErrorActionPreference = "Stop"

# --- Check prerequisites ---
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker not installed or not in PATH."
    exit 1
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Error "Maven not installed or not in PATH."
    exit 1
}

# --- Configuration ---
# WARNING: For local testing only — do not commit real credentials.
$CONTAINER_NAME     = "ai-db-test"
$DB_PORT            = 5555
$DB_NAME            = "ai-db-test"
$DB_USER            = "user"
$DB_PASS            = "pass"
$IMAGE              = "pgvector/pgvector:pg16"
$DB_READY_TIMEOUT   = 30

Write-Host "------------------------------------------------------------"
Write-Host "  STEP 1: Starting test database container..."
Write-Host "------------------------------------------------------------"

# Check if container already exists
$containerExists = docker ps -a --format '{{.Names}}' | Select-String "^$CONTAINER_NAME$"

if ($containerExists) {
    docker start $CONTAINER_NAME | Out-Null
    Write-Host "[+] Existing container '$CONTAINER_NAME' started."
} else {
    # Build Docker command as array to avoid PowerShell parsing issues
    $dockerArgs = @(
        "run", "-d",
        "--name", $CONTAINER_NAME,
        "-p", "${DB_PORT}:5432",
        "-e", "POSTGRES_DB=$DB_NAME",
        "-e", "POSTGRES_USER=$DB_USER",
        "-e", "POSTGRES_PASSWORD=$DB_PASS",
        $IMAGE
    )
    docker @dockerArgs | Out-Null
    Write-Host "[+] New container '$CONTAINER_NAME' created and started."
}

# --- Wait until database is ready ---
Write-Host -NoNewline "  Waiting for database at localhost:${DB_PORT} to be ready..."

$retries = $DB_READY_TIMEOUT
$ready = $false

do {
    Start-Sleep -Seconds 1
    $retries--

    $result = docker exec $CONTAINER_NAME pg_isready -U $DB_USER -d $DB_NAME 2>&1
    if ($result -match "accepting connections") {
        $ready = $true
    } else {
        Write-Host -NoNewline "."
    }
} until ($ready -or ($retries -le 0))

if (-not $ready) {
    Write-Host ""
    Write-Error "[!] Timeout: database did not become ready within ${DB_READY_TIMEOUT}s. Check 'docker logs $CONTAINER_NAME' for details."
    exit 1
}

Write-Host "`n[+] Database is READY."

Write-Host "------------------------------------------------------------"
Write-Host "  STEP 2: Executing Maven Lifecycle (verify)"
Write-Host "------------------------------------------------------------"

# Run Maven tests
mvn verify

Write-Host "------------------------------------------------------------"
Write-Host "  TEST RUN COMPLETED SUCCESSFULLY"
Write-Host "------------------------------------------------------------"

# Optional cleanup — uncomment to stop the container after tests:
# docker stop $CONTAINER_NAME

Write-Host "Done."