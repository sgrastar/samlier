package org.samlier.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.run.RunStatus;
import org.samlier.core.run.TestRun;

class SqliteRepositoriesTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void persistsPlansAndRuns() {
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        var plans = new SqlitePlanRepository(database, json);
        var runs = new SqliteRunRepository(database, json);
        var now = Instant.parse("2026-08-29T00:00:00Z");
        var plan = new TestPlan(
                "plan_0123456789ABCDEFGHJKMNPQRS",
                "Example IdP",
                PlanProfile.IDP_CORE,
                new TestPlan.Target(TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.HTTP_URL,
                Map.of("single_logout", true),
                TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(),
                now,
                now);
        plans.save(plan);
        var run = new TestRun("run_0123456789ABCDEFGHJKMNPQRS", plan.id(), RunStatus.CREATED,
                Reachability.UNKNOWN, Map.of(), now, now);
        runs.save(run);

        assertEquals(plan, plans.find(plan.id()).orElseThrow());
        assertEquals(run, runs.find(run.id()).orElseThrow());
        assertEquals(1, plans.list().size());
        assertEquals(1, runs.listForPlan(plan.id()).size());
        assertTrue(plans.delete(plan.id()));
    }
}
