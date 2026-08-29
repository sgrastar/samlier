package org.samlier.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.core.caseexec.CaseExecution;
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.OutboundAction;
import org.samlier.core.caseexec.OutboundKind;
import org.samlier.core.caseexec.OutboxStatus;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.run.RunStatus;
import org.samlier.core.run.TestRun;
import org.samlier.store.JsonCodec;
import org.samlier.store.SqliteCaseExecutionRepository;
import org.samlier.store.SqliteDatabase;
import org.samlier.store.SqlitePlanRepository;
import org.samlier.store.SqliteRunRepository;

class OutboxIncidentProjectionTest {
    private static final String RUN_ID = "run_0123456789ABCDEFGHJKMNPQRS";
    private static final Instant NOW = Instant.parse("2026-08-29T05:00:00Z");

    @TempDir java.nio.file.Path directory;

    @Test
    void reconstructsOnlyDurableUnknownDeliveryIncidents() {
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        var plan = plan();
        new SqlitePlanRepository(database, json).save(plan);
        new SqliteRunRepository(database, json).save(new TestRun(
                RUN_ID, plan.id(), RunStatus.RUNNING, Reachability.UNKNOWN, Map.of(), NOW, NOW));
        var repository = new SqliteCaseExecutionRepository(database, json);
        var unknown = action("action-unknown");
        var pending = action("action-pending");
        repository.apply(-1, execution("IIP-G03-a-idp-01"), List.of(unknown, pending));
        repository.transitionOutbox(
                unknown.actionId(), OutboxStatus.PENDING, OutboxStatus.SENDING,
                Map.of(), null, NOW.plusSeconds(1));
        repository.transitionOutbox(
                unknown.actionId(), OutboxStatus.SENDING, OutboxStatus.UNKNOWN_DELIVERY,
                Map.of("exception_type", "java.io.IOException"), null, NOW.plusSeconds(2));

        var incidents = new OutboxIncidentProjection(repository).incidents(RUN_ID);

        assertEquals(1, incidents.size());
        assertEquals("UNKNOWN_DELIVERY", incidents.getFirst().kind());
        assertEquals("IIP-G03-a-idp-01", incidents.getFirst().caseId());
        assertEquals("action-unknown", incidents.getFirst().actionId());
    }

    private CaseExecution execution(String caseId) {
        return new CaseExecution(
                RUN_ID, caseId, 0, CaseExecutionStatus.RUNNING,
                new CaseState("send", Map.of()), null, null, NOW);
    }

    private OutboundAction action(String id) {
        return new OutboundAction(
                id, OutboundKind.AUTHN_REQUEST, new byte[] {1}, URI.create("https://idp.example/sso"), false);
    }

    private TestPlan plan() {
        return new TestPlan(
                "plan_0123456789ABCDEFGHJKMNPQRS", "Outbox incident test", PlanProfile.IDP_CORE,
                new TestPlan.Target(
                        TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), NOW, NOW);
    }
}
