package com.samlscope.core.plan;

import java.util.List;
import java.util.Optional;

public interface PlanRepository {
    List<TestPlan> list();
    Optional<TestPlan> find(String id);
    void save(TestPlan plan);
    boolean delete(String id);
}
