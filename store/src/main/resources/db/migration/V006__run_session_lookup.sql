CREATE UNIQUE INDEX run_access_grants_session_token_hash
    ON run_access_grants(session_token_hash)
    WHERE session_token_hash IS NOT NULL;
