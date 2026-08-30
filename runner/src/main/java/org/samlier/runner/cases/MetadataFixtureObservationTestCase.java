package org.samlier.runner.cases;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.ConfigurationFailureSemantics;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.transcript.Direction;

/** Evaluates accept/reject metadata fixtures using fetches plus Run-correlated SAML traffic. */
public final class MetadataFixtureObservationTestCase
        implements TestCase, ConfigurationPrompt, ProtocolEvidenceCase {
    private static final String PHASE = "await-metadata-fixture-probe";
    private static final String CONTROL = "control";
    private final String id;
    private final TargetRole role;
    private final List<Fixture> fixtures;
    private final ConfigurationFailureSemantics configurationSemantics;

    public MetadataFixtureObservationTestCase(
            String id,
            TargetRole role,
            List<Fixture> fixtures,
            ConfigurationFailureSemantics configurationSemantics) {
        this.id = text(id, "id");
        this.role = Objects.requireNonNull(role, "role");
        this.fixtures = List.copyOf(fixtures);
        this.configurationSemantics = Objects.requireNonNull(
                configurationSemantics, "configurationSemantics");
        if (fixtures.isEmpty()) throw new IllegalArgumentException("fixtures must not be empty");
        var ids = new LinkedHashSet<String>();
        for (var fixture : fixtures) {
            if (CONTROL.equals(fixture.variant())) {
                throw new IllegalArgumentException("control is reserved for the baseline observation");
            }
            if (!ids.add(fixture.variant())) throw new IllegalArgumentException("Duplicate fixture variant");
        }
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return role; }

    @Override
    public String instructionEn() {
        var value = new StringBuilder()
                .append("Configure the target once with `/p/<plan-id>/metadata/live?run=<run-id>`. ")
                .append("Select `control`, trigger the target's standard refresh or re-import, and complete a ")
                .append("working SAML flow. Then repeat the refresh/re-import and flow attempt for these fixtures:");
        fixtures.forEach(fixture -> value.append("\n- `").append(fixture.variant()).append("`: expected ")
                .append(fixture.behavior().name().toLowerCase(java.util.Locale.ROOT))
                .append(" — ").append(fixture.purpose()));
        return value.append("\nUse the Run-level protocol-evidence action only after every attempt. The Suite ")
                .append("derives the outcome; the operator does not enter a verdict.").toString();
    }

    @Override
    public CaseStep start(CaseContext context) {
        return new CaseStep.AwaitConfig(
                new CaseState(PHASE, Map.of()), List.of(),
                "metadata-fixture-probe", Duration.ofDays(7));
    }

    @Override
    public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        if (!PHASE.equals(state.phase())) throw new IllegalArgumentException("Unexpected metadata fixture phase");
        if (event instanceof CaseEvent.ConfigConfirmed) return new CaseStep.Finish(evaluate(context));
        if (event instanceof CaseEvent.ConfigUnavailable unavailable) {
            if (unavailable.issue() == CaseEvent.ConfigurationIssue.CAPABILITY_ABSENT
                    && configurationSemantics == ConfigurationFailureSemantics.NORMATIVE_CAPABILITY) {
                return new CaseStep.Finish(new CaseOutcome(
                        Outcome.VIOLATED, null, "capability_absent", "configuration.capability-absent",
                        List.of(), Map.of(
                                "configuration_issue", "capability_absent",
                                "configuration_note", unavailable.note())));
            }
            return new CaseStep.Finish(new CaseOutcome(
                    Outcome.NOT_VERIFIED, "metadata_fixture_probe_unavailable",
                    "metadata.fixture-probe.unavailable", "metadata.fixture-probe.unavailable",
                    List.of(), Map.of(
                            "configuration_issue", unavailable.issue().name().toLowerCase(java.util.Locale.ROOT),
                            "configuration_note", unavailable.note())));
        }
        if (event instanceof CaseEvent.TimedOut) {
            return new CaseStep.Finish(CaseOutcome.notVerified(
                    "metadata_fixture_probe_timeout", "metadata.fixture-probe.timeout"));
        }
        if (event instanceof CaseEvent.Aborted) {
            return new CaseStep.Finish(CaseOutcome.notVerified(
                    "metadata_fixture_probe_skipped", "metadata.fixture-probe.skipped"));
        }
        throw new IllegalArgumentException("Expected metadata fixture configuration completion");
    }

    @Override
    public EvidenceStatus evidenceStatus(CaseContext context) {
        var observation = observe(context);
        var required = new ArrayList<String>();
        required.add("fetched:" + CONTROL);
        required.add("used:" + CONTROL);
        fixtures.forEach(fixture -> required.add("fetched:" + fixture.variant()));
        var completed = required.stream().filter(value -> {
            var separator = value.indexOf(':');
            var kind = value.substring(0, separator);
            var variant = value.substring(separator + 1);
            return "fetched".equals(kind)
                    ? observation.fetched().contains(variant)
                    : observation.used().contains(variant);
        }).toList();
        return new EvidenceStatus(observation.ready(), required, completed, observation.details());
    }

    private CaseOutcome evaluate(CaseContext context) {
        var observation = observe(context);
        if (!observation.ready()) {
            return new CaseOutcome(
                    Outcome.NOT_VERIFIED, "metadata_fixture_probe_incomplete",
                    "metadata.fixture-probe.incomplete", "metadata.fixture-probe.incomplete",
                    observation.evidence(), observation.details());
        }
        var mismatches = new ArrayList<String>();
        for (var fixture : fixtures) {
            var used = observation.used().contains(fixture.variant());
            if (fixture.behavior() == Behavior.ACCEPT && !used) {
                mismatches.add(fixture.variant() + ":expected_acceptance");
            } else if (fixture.behavior() == Behavior.REJECT && used) {
                mismatches.add(fixture.variant() + ":expected_rejection");
            }
        }
        var details = new LinkedHashMap<String, Object>(observation.details());
        details.put("mismatches", List.copyOf(mismatches));
        return new CaseOutcome(
                mismatches.isEmpty() ? Outcome.SATISFIED : Outcome.VIOLATED,
                null,
                mismatches.isEmpty() ? "metadata.fixture-probe.satisfied" : "metadata.fixture-probe.violated",
                mismatches.isEmpty() ? "metadata.fixture-probe.satisfied" : "metadata.fixture-probe.violated",
                observation.evidence(), details);
    }

    private Observation observe(CaseContext context) {
        var relevant = new LinkedHashSet<String>();
        relevant.add(CONTROL);
        fixtures.forEach(fixture -> relevant.add(fixture.variant()));
        var fetched = new LinkedHashSet<String>();
        var used = new LinkedHashSet<String>();
        var evidence = new ArrayList<EvidenceRef>();
        for (var entry : context.transcript().list(context.runId())) {
            if (entry.direction() != Direction.INBOUND) continue;
            if ("MetadataFetch".equals(entry.samlSummary().get("type"))) {
                var variant = String.valueOf(entry.samlSummary().get("variant"));
                if (relevant.contains(variant)) {
                    fetched.add(variant);
                    evidence.add(new EvidenceRef("transcript", "transcript:" + entry.id()));
                }
            }
            if (entry.decodedSamlBytes() <= 0 || entry.url() == null) continue;
            for (var variant : relevant) {
                if (entry.url().contains("mdv=" + variant)
                        && entry.url().contains("run=" + context.runId())) {
                    used.add(variant);
                    evidence.add(new EvidenceRef("transcript", "transcript:" + entry.id()));
                }
            }
        }
        var details = Map.<String, Object>of(
                "fixtures", fixtures.stream().map(Fixture::variant).toList(),
                "fetched_variants", List.copyOf(fetched),
                "used_variants", List.copyOf(used));
        return new Observation(
                fetched.contains(CONTROL) && used.contains(CONTROL)
                        && fixtures.stream().allMatch(value -> fetched.contains(value.variant())),
                fetched, used, distinct(evidence), details);
    }

    private static List<EvidenceRef> distinct(List<EvidenceRef> evidence) {
        return evidence.stream().distinct().toList();
    }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    public enum Behavior { ACCEPT, REJECT }

    public record Fixture(String variant, Behavior behavior, String purpose) {
        public Fixture {
            variant = text(variant, "variant");
            behavior = Objects.requireNonNull(behavior, "behavior");
            purpose = text(purpose, "purpose");
        }
    }

    private record Observation(
            boolean ready,
            Set<String> fetched,
            Set<String> used,
            List<EvidenceRef> evidence,
            Map<String, Object> details) {}
}
