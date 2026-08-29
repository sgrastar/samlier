CREATE TABLE IF NOT EXISTS case_executions (
    run_id TEXT NOT NULL REFERENCES runs(id) ON DELETE CASCADE,
    case_id TEXT NOT NULL,
    revision INTEGER NOT NULL,
    status TEXT NOT NULL,
    document_json TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (run_id, case_id)
);

CREATE TABLE IF NOT EXISTS outbox_actions (
    action_id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL,
    case_id TEXT NOT NULL,
    kind TEXT NOT NULL,
    status TEXT NOT NULL,
    action_json TEXT NOT NULL,
    send_result_json TEXT NOT NULL,
    transcript_entry_id TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (run_id, case_id) REFERENCES case_executions(run_id, case_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS outbox_run_status_idx ON outbox_actions(run_id, status, created_at);
