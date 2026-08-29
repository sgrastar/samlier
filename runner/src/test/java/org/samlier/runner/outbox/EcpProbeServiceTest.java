package org.samlier.runner.outbox;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.core.caseexec.OutboxStatus;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.run.RunStatus;
import org.samlier.core.run.TestRun;
import org.samlier.runner.OutboundPolicy;
import org.samlier.store.JsonCodec;
import org.samlier.store.SqliteCaseExecutionRepository;
import org.samlier.store.SqliteDatabase;
import org.samlier.store.SqlitePlanRepository;
import org.samlier.store.SqliteRunRepository;

class EcpProbeServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");
    private static final String RUN = "run_0123456789ABCDEFGHJKMNPQRS";
    @TempDir java.nio.file.Path directory;
    private SqliteCaseExecutionRepository repository;
    private InMemoryEphemeralCredentialProvider credentials;

    @BeforeEach
    void setUp() {
        var json = new JsonCodec();
        var database = new SqliteDatabase(directory);
        var plan = new TestPlan(
                "plan_0123456789ABCDEFGHJKMNPQRS", "ECP probe", PlanProfile.IDP_FULL,
                new TestPlan.Target(TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), NOW, NOW);
        new SqlitePlanRepository(database, json).save(plan);
        new SqliteRunRepository(database, json).save(new TestRun(
                RUN, plan.id(), RunStatus.COMPLETED, Reachability.CONFIRMED, Map.of(), NOW, NOW));
        repository = new SqliteCaseExecutionRepository(database, json);
        credentials = new InMemoryEphemeralCredentialProvider();
    }

    @Test
    void persistsOnlyTheEnvelopeAndConsumesTheCredentialThroughTheDispatcher() {
        var secret = "alice:secret".getBytes(StandardCharsets.UTF_8);
        var sender = (OutboundSender) (runId, action, credential) -> {
            assertEquals(RUN, runId);
            assertArrayEquals(secret, credential);
            assertTrue(new String(action.payload(), StandardCharsets.UTF_8).contains("Envelope"));
            return new OutboundSender.SendResult(false, Map.of("status", 200), "tx-response");
        };
        var dispatcher = new OutboundDispatcher(
                repository, sender, credentials, new OutboundPolicy(true),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var service = new EcpProbeService(
                repository, credentials, dispatcher, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.execute(
                RUN, URI.create("https://idp.example/ecp"),
                "<Envelope/>".getBytes(StandardCharsets.UTF_8), secret);

        assertEquals(OutboundDispatcher.State.SENT, result.dispatchState());
        assertEquals(OutboxStatus.SENT, result.outboxStatus());
        assertEquals("tx-response", result.responseTranscriptId());
        assertTrue(credentials.credentialFor(RUN, result.actionId()).isEmpty());
        var action = repository.findOutbox(result.actionId()).orElseThrow().action();
        assertEquals(true, action.requiresEphemeralCredential());
        assertEquals(false, new String(action.payload(), StandardCharsets.UTF_8).contains("secret"));
    }
}
