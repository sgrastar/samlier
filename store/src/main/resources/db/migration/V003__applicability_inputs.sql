CREATE TABLE applicability_inputs (
    run_id TEXT NOT NULL,
    predicate TEXT NOT NULL,
    document_json TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (run_id, predicate),
    FOREIGN KEY (run_id) REFERENCES runs(id) ON DELETE CASCADE
);
