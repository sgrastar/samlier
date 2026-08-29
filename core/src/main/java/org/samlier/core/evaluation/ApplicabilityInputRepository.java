package org.samlier.core.evaluation;

import java.time.Instant;
import java.util.Optional;

public interface ApplicabilityInputRepository {
    Optional<ApplicabilityInput> find(String runId, String predicate);
    void save(String runId, String predicate, ApplicabilityInput input, Instant updatedAt);
}
