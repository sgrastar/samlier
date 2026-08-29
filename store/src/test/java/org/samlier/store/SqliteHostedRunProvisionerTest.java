package org.samlier.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.core.access.RunAccessGrant;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.run.RunStatus;
import org.samlier.core.run.TestRun;

class SqliteHostedRunProvisionerTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void persistsPlanRunAndGrantAsOneProvisioningOperation() {
        var fixture = fixture();
        var plan = plan("plan_first", "https://target.example/idp");
        var run = run("run_first", plan.id(), RunStatus.CREATED);
        var grant = grant(run.id(), 'a');

        assertTrue(fixture.provisioner.createPlanWithInitialRun(plan, run, grant));
        assertEquals(plan, fixture.plans.find(plan.id()).orElseThrow());
        assertEquals(run, fixture.runs.find(run.id()).orElseThrow());
        assertEquals(grant, fixture.grants.find(run.id()).orElseThrow());
    }

    @Test
    void rejectsAnotherActiveRunForTheSameTargetWithoutPersistingAnyRecord() {
        var fixture = fixture();
        var firstPlan = plan("plan_first", "https://target.example/idp");
        var firstRun = run("run_first", firstPlan.id(), RunStatus.CREATED);
        assertTrue(fixture.provisioner.createPlanWithInitialRun(
                firstPlan, firstRun, grant(firstRun.id(), 'a')));

        var rejectedPlan = plan("plan_rejected", firstPlan.target().entityId());
        var rejectedRun = run("run_rejected", rejectedPlan.id(), RunStatus.CREATED);
        assertFalse(fixture.provisioner.createPlanWithInitialRun(
                rejectedPlan, rejectedRun, grant(rejectedRun.id(), 'b')));

        assertFalse(fixture.plans.find(rejectedPlan.id()).isPresent());
        assertFalse(fixture.runs.find(rejectedRun.id()).isPresent());
        assertFalse(fixture.grants.find(rejectedRun.id()).isPresent());
    }

    @Test
    void permitsAReplacementAfterThePriorRunBecomesTerminal() {
        var fixture = fixture();
        var firstPlan = plan("plan_first", "https://target.example/idp");
        var firstRun = run("run_first", firstPlan.id(), RunStatus.CREATED);
        assertTrue(fixture.provisioner.createPlanWithInitialRun(
                firstPlan, firstRun, grant(firstRun.id(), 'a')));
        fixture.runs.save(run(firstRun.id(), firstPlan.id(), RunStatus.COMPLETED));

        var nextRun = run("run_next", firstPlan.id(), RunStatus.CREATED);
        assertTrue(fixture.provisioner.createRun(
                firstPlan, nextRun, grant(nextRun.id(), 'b')));
        assertEquals(nextRun, fixture.runs.find(nextRun.id()).orElseThrow());
        assertEquals(1, fixture.plans.list().size());
    }

    @Test
    void rollsBackThePlanWhenAChildInsertFails() {
        var fixture = fixture();
        var existingPlan = plan("plan_existing", "https://existing.example/idp");
        var existingRun = run("run_collision", existingPlan.id(), RunStatus.COMPLETED);
        fixture.plans.save(existingPlan);
        fixture.runs.save(existingRun);

        var rejectedPlan = plan("plan_rolled_back", "https://other.example/idp");
        var collidingRun = run(existingRun.id(), rejectedPlan.id(), RunStatus.CREATED);
        assertThrows(StoreException.class, () -> fixture.provisioner.createPlanWithInitialRun(
                rejectedPlan, collidingRun, grant(collidingRun.id(), 'c')));

        assertFalse(fixture.plans.find(rejectedPlan.id()).isPresent());
        assertEquals(existingRun, fixture.runs.find(existingRun.id()).orElseThrow());
        assertFalse(fixture.grants.find(existingRun.id()).isPresent());
    }

    @Test
    void serializesConcurrentProvisioningForTheSameTarget() throws Exception {
        var fixture = fixture();
        var firstPlan = plan("plan_first", "https://target.example/idp");
        var secondPlan = plan("plan_second", firstPlan.target().entityId());
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> provisionAfterStart(fixture, firstPlan, "run_first", 'a', ready, start));
            var second = executor.submit(() -> provisionAfterStart(fixture, secondPlan, "run_second", 'b', ready, start));
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        }
        assertEquals(1, fixture.plans.list().size());
    }

    private boolean provisionAfterStart(
            Fixture fixture, TestPlan plan, String runId, char digest,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(2, TimeUnit.SECONDS));
        var run = run(runId, plan.id(), RunStatus.CREATED);
        return fixture.provisioner.createPlanWithInitialRun(plan, run, grant(run.id(), digest));
    }

    private Fixture fixture() {
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        return new Fixture(
                new SqliteHostedRunProvisioner(database, json),
                new SqlitePlanRepository(database, json),
                new SqliteRunRepository(database, json),
                new SqliteRunAccessGrantRepository(database));
    }

    private TestPlan plan(String id, String entityId) {
        return new TestPlan(id, id, PlanProfile.IDP_CORE,
                new TestPlan.Target(TargetKind.IDP, entityId,
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, entityId + "/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), Instant.EPOCH, Instant.EPOCH);
    }

    private TestRun run(String id, String planId, RunStatus status) {
        return new TestRun(id, planId, status, Reachability.UNKNOWN, Map.of(), Instant.EPOCH, Instant.EPOCH);
    }

    private RunAccessGrant grant(String runId, char value) {
        return new RunAccessGrant(runId, "sha256:" + String.valueOf(value).repeat(64),
                null, null, Instant.EPOCH, false);
    }

    private record Fixture(
            SqliteHostedRunProvisioner provisioner,
            SqlitePlanRepository plans,
            SqliteRunRepository runs,
            SqliteRunAccessGrantRepository grants) {}
}
