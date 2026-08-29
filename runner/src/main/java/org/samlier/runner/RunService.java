package org.samlier.runner;

import java.time.Clock;
import java.util.Map;
import org.samlier.core.Identifiers;
import org.samlier.core.plan.PlanRepository;
import org.samlier.core.run.Reachability;
import org.samlier.core.run.RunRepository;
import org.samlier.core.run.RunStatus;
import org.samlier.core.run.TestRun;

public final class RunService {
    private final PlanRepository plans;
    private final RunRepository runs;
    private final RunEventBus events;
    private final Clock clock;

    public RunService(PlanRepository plans, RunRepository runs, RunEventBus events, Clock clock) {
        this.plans = plans;
        this.runs = runs;
        this.events = events;
        this.clock = clock;
    }

    public TestRun create(String planId) {
        plans.find(planId).orElseThrow(() -> new IllegalArgumentException("Unknown Test Plan"));
        var run = prepare(planId);
        runs.save(run);
        publishCreated(run);
        return run;
    }

    /** Builds a Run for a caller that will persist it in a larger transaction. */
    public TestRun prepare(String planId) {
        var now = clock.instant();
        return new TestRun(Identifiers.newId("run"), planId, RunStatus.CREATED,
                Reachability.UNKNOWN, Map.of(), now, now);
    }

    /** Publishes the creation event after an external transaction commits. */
    public void publishCreated(TestRun run) {
        events.publish(new RunEvent(
                run.id(), "run.created", run.createdAt(), Map.of("status", run.status().name())));
    }

    public TestRun update(TestRun run, RunStatus status, Reachability reachability, Map<String, Object> context) {
        var updated = new TestRun(run.id(), run.planId(), status, reachability, context,
                run.createdAt(), clock.instant());
        runs.save(updated);
        events.publish(new RunEvent(updated.id(), "run.updated", updated.updatedAt(),
                Map.of("status", updated.status().name(), "reachability", updated.targetToSuiteReachability().name())));
        return updated;
    }
}
