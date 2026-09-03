package com.samlscope.core.access;

import java.util.Optional;

public interface RunAccessGrantRepository {
    Optional<RunAccessGrant> find(String runId);
    Optional<RunAccessGrant> findBySessionTokenHash(String sessionTokenHash);
    void save(RunAccessGrant grant);
    boolean delete(String runId);
}
