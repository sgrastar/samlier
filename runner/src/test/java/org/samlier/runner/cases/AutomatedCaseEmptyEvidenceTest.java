package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.run.RunStatus;
import org.samlier.core.run.TestRun;
import org.samlier.runner.AutomatedCaseRunner;
import org.samlier.runner.CaseExecutionService;
import org.samlier.runner.DefaultCaseContext;
import org.samlier.saml.crypto.FilePlanKeyStore;
import org.samlier.store.FileTranscriptRecorder;
import org.samlier.store.JsonCodec;
import org.samlier.store.SqliteCaseExecutionRepository;
import org.samlier.store.SqliteDatabase;
import org.samlier.store.SqlitePlanRepository;
import org.samlier.store.SqliteRunRepository;

/** Cross-case safety invariant: absence of target evidence is never target non-conformance. */
class AutomatedCaseEmptyEvidenceTest {
    private static final String RUN_ID = "run_0123456789ABCDEFGHJKMNPQRS";
    private static final String PLAN_ID = "plan_0123456789ABCDEFGHJKMNPQRS";
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");
    @TempDir java.nio.file.Path directory;

    @Test
    void everyPassiveIdpCaseFailsSafeWithoutTranscriptEvidence() {
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        var plan = plan();
        new SqlitePlanRepository(database, json).save(plan);
        new SqliteRunRepository(database, json).save(new TestRun(
                RUN_ID, PLAN_ID, RunStatus.RUNNING, Reachability.CONFIRMED, Map.of(), NOW, NOW));
        var transcript = new FileTranscriptRecorder(database, json, directory);
        var executions = new SqliteCaseExecutionRepository(database, json);
        var credentials = new FilePlanKeyStore(directory, Clock.fixed(NOW, ZoneOffset.UTC)).getOrCreate(PLAN_ID);
        var selector = SamlOptionalFieldObservationCase.Selector.element(new QName(
                "urn:oasis:names:tc:SAML:2.0:protocol", "Extensions"));
        var dependencies = new AutomatedCaseDependencies(
                transcript,
                attributeFixtures(),
                Map.of("IIP-SSO07-a-idp-01", selector, "IIP-SSO07-a-sp-01", selector),
                List.of(credentials.certificate()),
                "https://suite.example/p/" + PLAN_ID,
                ignored -> Optional.of(credentials.privateKey()),
                (runId, identifier) -> PrincipalIdentityResolver.Resolution.unknown(),
                executions,
                new IdpErrorProbeConfiguration(
                        URI.create("https://idp.example/sso"), "https://suite.example/p/" + PLAN_ID,
                        URI.create("https://suite.example/p/" + PLAN_ID + "/sp/acs/0"),
                        Duration.ofMinutes(2), true, true, true));
        var registry = AutomatedCaseRegistry.create(dependencies);
        var runner = new AutomatedCaseRunner(registry, new CaseExecutionService(executions));
        var context = new DefaultCaseContext(
                RUN_ID, TargetRole.IDP, Clock.fixed(NOW, ZoneOffset.UTC), plan.parameters(),
                Reachability.CONFIRMED, transcript, true);

        var snapshot = runner.startReady(RUN_ID, plan.profile(), context);

        assertEquals(registry.forRole(TargetRole.IDP).stream()
                .filter(value -> AutomatedCaseRegistry.includedIn(value.id(), plan.profile())).count(), snapshot.size());
        assertFalse(snapshot.stream().anyMatch(value -> AutomatedCaseRegistry.fullProfileCaseIds().contains(value.caseId())));
        var finished = snapshot.stream().filter(value -> value.status() == CaseExecutionStatus.FINISHED).toList();
        assertEquals(snapshot.size() - 1, finished.size(), "Only the active error probe should still be waiting");
        assertFalse(finished.stream().anyMatch(value -> value.outcome().outcome() == Outcome.VIOLATED),
                () -> "No-evidence violations: " + finished.stream()
                        .filter(value -> value.outcome().outcome() == Outcome.VIOLATED)
                        .map(value -> value.caseId()).toList());
        assertEquals(1, executions.listOutbox(RUN_ID).size());
    }

    @Test
    void everyPassiveSpCaseFailsSafeWithoutTranscriptEvidence() {
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        var plan = new TestPlan(
                PLAN_ID, "Empty SP evidence", PlanProfile.SP_CORE,
                new TestPlan.Target(TargetKind.SP, "https://sp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://sp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), NOW, NOW);
        new SqlitePlanRepository(database, json).save(plan);
        new SqliteRunRepository(database, json).save(new TestRun(
                RUN_ID, PLAN_ID, RunStatus.RUNNING, Reachability.CONFIRMED, Map.of(), NOW, NOW));
        var transcript = new FileTranscriptRecorder(database, json, directory);
        var executions = new SqliteCaseExecutionRepository(database, json);
        var credentials = new FilePlanKeyStore(directory, Clock.fixed(NOW, ZoneOffset.UTC)).getOrCreate(PLAN_ID);
        var selector = SamlOptionalFieldObservationCase.Selector.element(new QName(
                "urn:oasis:names:tc:SAML:2.0:protocol", "Extensions"));
        var registry = AutomatedCaseRegistry.create(new AutomatedCaseDependencies(
                transcript, attributeFixtures(),
                Map.of("IIP-SSO07-a-idp-01", selector, "IIP-SSO07-a-sp-01", selector),
                List.of(credentials.certificate()), "https://suite.example/p/" + PLAN_ID,
                ignored -> Optional.of(credentials.privateKey()),
                (runId, identifier) -> PrincipalIdentityResolver.Resolution.unknown(), executions,
                new IdpErrorProbeConfiguration(
                        URI.create("https://idp.example/sso"), "https://suite.example/p/" + PLAN_ID,
                        URI.create("https://suite.example/p/" + PLAN_ID + "/sp/acs/0"),
                        Duration.ofMinutes(2), false, false, false)));
        var runner = new AutomatedCaseRunner(registry, new CaseExecutionService(executions));
        var context = new DefaultCaseContext(
                RUN_ID, TargetRole.SP, Clock.fixed(NOW, ZoneOffset.UTC), plan.parameters(),
                Reachability.CONFIRMED, transcript, true);

        var snapshot = runner.startReady(RUN_ID, plan.profile(), context);

        assertEquals(registry.forRole(TargetRole.SP).stream()
                .filter(value -> AutomatedCaseRegistry.includedIn(value.id(), plan.profile())).count(), snapshot.size());
        assertFalse(snapshot.stream().anyMatch(value -> AutomatedCaseRegistry.fullProfileCaseIds().contains(value.caseId())));
        assertEquals(snapshot.size(), snapshot.stream()
                .filter(value -> value.status() == CaseExecutionStatus.FINISHED).count());
        assertFalse(snapshot.stream().anyMatch(value -> value.outcome().outcome() == Outcome.VIOLATED),
                () -> "No-evidence violations: " + snapshot.stream()
                        .filter(value -> value.outcome().outcome() == Outcome.VIOLATED)
                        .map(value -> value.caseId()).toList());
        assertEquals(0, executions.listOutbox(RUN_ID).size());
    }

    private Map<String, SamlAttributeReleaseFixture> attributeFixtures() {
        return Map.of(
                "IIP-SSO01-dj-idp-01", new SamlAttributeReleaseFixture("no-values", null, List.of()),
                "IIP-SSO01-dk-idp-01", new SamlAttributeReleaseFixture(
                        "empty", null, List.of(SamlAttributeReleaseFixture.EmptyValue.INSTANCE)),
                "IIP-SSO01-dl-idp-01", new SamlAttributeReleaseFixture(
                        "null", null, List.of(SamlAttributeReleaseFixture.NullValue.INSTANCE)),
                "IIP-SSO01-du-idp-01", new SamlAttributeReleaseFixture(
                        "discrete", null, List.of(
                                new SamlAttributeReleaseFixture.TextValue("one"),
                                new SamlAttributeReleaseFixture.TextValue("two"))));
    }

    private TestPlan plan() {
        return new TestPlan(
                PLAN_ID, "Empty evidence", PlanProfile.IDP_CORE,
                new TestPlan.Target(TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), NOW, NOW);
    }
}
