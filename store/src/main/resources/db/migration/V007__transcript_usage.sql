CREATE TABLE transcript_usage (
    run_id TEXT PRIMARY KEY REFERENCES runs(id) ON DELETE CASCADE,
    entry_count INTEGER NOT NULL CHECK(entry_count >= 0),
    stored_bytes INTEGER NOT NULL CHECK(stored_bytes >= 0),
    rejected INTEGER NOT NULL DEFAULT 0 CHECK(rejected IN (0, 1)),
    rejected_at TEXT
);

INSERT INTO transcript_usage(run_id, entry_count, stored_bytes)
SELECT run_id,
       COUNT(*),
       COALESCE(SUM(
           COALESCE(CAST(json_extract(document_json, '$.bodyBytes') AS INTEGER), 0)
           + COALESCE(CAST(json_extract(document_json, '$.decodedSamlBytes') AS INTEGER), 0)
       ), 0)
FROM transcript_entries
GROUP BY run_id;

CREATE TABLE transcript_global_usage (
    singleton INTEGER PRIMARY KEY CHECK(singleton = 1),
    entry_count INTEGER NOT NULL CHECK(entry_count >= 0),
    stored_bytes INTEGER NOT NULL CHECK(stored_bytes >= 0)
);

INSERT INTO transcript_global_usage(singleton, entry_count, stored_bytes)
SELECT 1,
       COUNT(*),
       COALESCE(SUM(
           COALESCE(CAST(json_extract(document_json, '$.bodyBytes') AS INTEGER), 0)
           + COALESCE(CAST(json_extract(document_json, '$.decodedSamlBytes') AS INTEGER), 0)
       ), 0)
FROM transcript_entries;

CREATE TABLE transcript_ingress_rate (
    run_id TEXT PRIMARY KEY REFERENCES runs(id) ON DELETE CASCADE,
    window_minute INTEGER NOT NULL,
    request_count INTEGER NOT NULL CHECK(request_count >= 0)
);

CREATE TABLE transcript_global_ingress_rate (
    singleton INTEGER PRIMARY KEY CHECK(singleton = 1),
    window_minute INTEGER NOT NULL,
    request_count INTEGER NOT NULL CHECK(request_count >= 0)
);

-- Hosted Plans created by the same anonymous provisioning source share resource
-- limits even when an attacker rotates Run and Plan identifiers. The value is an
-- application-generated one-way fingerprint. The source address is not stored.
CREATE TABLE hosted_plan_owners (
    plan_id TEXT PRIMARY KEY REFERENCES plans(id) ON DELETE CASCADE,
    owner_id TEXT NOT NULL
);

CREATE INDEX hosted_plan_owners_owner_id ON hosted_plan_owners(owner_id);
