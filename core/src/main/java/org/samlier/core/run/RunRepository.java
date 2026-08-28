package org.samlier.core.run;

import java.util.List;
import java.util.Optional;

public interface RunRepository {
    List<TestRun> listForPlan(String planId);
    Optional<TestRun> find(String id);
    void save(TestRun run);
}
