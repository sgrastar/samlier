package org.samlier.runner.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.CaseRun;
import org.samlier.core.evaluation.CoverageCatalog;
import org.samlier.core.evaluation.Evaluator;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.evaluation.Rfc2119Level;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.run.RunStatus;
import org.samlier.core.run.TestRun;

class DefaultResultContextProviderTest {
    @Test
    void derivesTraceableUrlsAndMetadataDigestFromTheEvaluatedSnapshot() {
        var plan = plan();
        var run = new TestRun("run_0123456789ABCDEFGHJKMNPQRS", plan.id(), RunStatus.COMPLETED,
                Reachability.CONFIRMED, Map.of(), Instant.EPOCH, Instant.EPOCH);
        var catalog = new CoverageCatalog(List.of(new CoverageCatalog.Obligation(
                "REQ.a", "REQ", Rfc2119Level.MUST, List.of(TargetRole.IDP), null,
                CoverageCatalog.Testability.AUTOMATED, CoverageCatalog.ProfileScope.CORE)));
        var caseRun = CaseRun.completed("REQ-a-idp-01", "REQ.a",
                CaseOutcome.of(Outcome.SATISFIED, "ok", List.of()));
        var result = Evaluator.evaluate(catalog, plan, List.of(), List.of(caseRun), List.of());
        var provider = new DefaultResultContextProvider(
                new ResultDocumentContext.Suite("Samlier", "0.1", digest('a'), "self-hosted"),
                new ResultDocumentContext.EvaluationComponents(digest('b'), digest('c'), digest('d'), "1", "1"),
                URI.create("https://docs.example/requirements"), URI.create("https://docs.example/cases"),
                snapshotRun -> {
                    assertEquals(run.id(), snapshotRun.id());
                    return "<md/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                });

        var context = provider.context(run, plan, List.of(caseRun), result);

        assertEquals("https://docs.example/requirements#REQ", context.requirementSpecUrls().get("REQ"));
        assertEquals("https://docs.example/cases#REQ-a-idp-01",
                context.caseDefinitionUrls().get("REQ-a-idp-01"));
        assertEquals(EvaluationArtifactDigests.digestBytes("<md/>".getBytes()), context.target().metadataDigest());
        assertThrows(IllegalStateException.class, () -> new DefaultResultContextProvider(
                context.suite(), context.evaluationComponents(), URI.create("https://docs.example/requirements"),
                URI.create("https://docs.example/cases"), ignored -> new byte[0])
                .context(run, plan, List.of(caseRun), result));
    }

    private TestPlan plan() {
        return new TestPlan("plan_0123456789ABCDEFGHJKMNPQRS", "Example IdP", PlanProfile.IDP_CORE,
                new TestPlan.Target(TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), Instant.EPOCH, Instant.EPOCH);
    }

    private String digest(char value) { return "sha256:" + String.valueOf(value).repeat(64); }
}
