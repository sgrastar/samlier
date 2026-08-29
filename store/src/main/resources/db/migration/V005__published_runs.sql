CREATE TABLE published_runs (
    run_id TEXT PRIMARY KEY REFERENCES runs(id) ON DELETE CASCADE,
    published_at TEXT NOT NULL
)
