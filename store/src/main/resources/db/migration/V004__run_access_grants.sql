CREATE TABLE run_access_grants (
    run_id TEXT PRIMARY KEY REFERENCES runs(id) ON DELETE CASCADE,
    access_token_hash TEXT NOT NULL,
    session_token_hash TEXT,
    csrf_token_hash TEXT,
    updated_at TEXT NOT NULL,
    revoked INTEGER NOT NULL DEFAULT 0 CHECK (revoked IN (0, 1)),
    CHECK ((session_token_hash IS NULL) = (csrf_token_hash IS NULL))
)
