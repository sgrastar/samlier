package org.samlier.runner.cases;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.transcript.Direction;

/**
 * Observes a target consuming controlled metadata variants. Configuration is a setup step; the
 * verdict is derived from Suite-recorded fetches and variant-correlated inbound SAML only.
 */
public final class MetadataConsumerObservationTestCase implements TestCase, ConfigurationPrompt {
    public enum Rule { PERMITTED_IDENTITY_TRANSFORM, EXCLUDED_CONTENT, OMITTED_KEY_INFO }

    private static final String CONFIGURATION_PHASE = "await-metadata-consumer-probe";
    private static final String CONTROL = "control";
    private final String id;
    private final TargetRole role;
    private final Rule rule;
    private final List<String> variants;
    private final String instructionEn;

    public MetadataConsumerObservationTestCase(String id, TargetRole role, Rule rule) {
        this.id = required(id, "id");
        this.role = java.util.Objects.requireNonNull(role, "role");
        this.rule = java.util.Objects.requireNonNull(rule, "rule");
        this.variants = switch (rule) {
            case PERMITTED_IDENTITY_TRANSFORM -> List.of("xpath-identity");
            case EXCLUDED_CONTENT -> List.of(
                    "xpath-exclude-role-descriptors",
                    "xpath-exclude-endpoints",
                    "xpath-exclude-key-descriptors");
            case OMITTED_KEY_INFO -> List.of("no-key-info");
        };
        this.instructionEn = instruction(rule, variants);
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return role; }
    @Override public String instructionEn() { return instructionEn; }

    @Override
    public CaseStep start(CaseContext context) {
        return new CaseStep.AwaitConfig(
                new CaseState(CONFIGURATION_PHASE, Map.of()), List.of(),
                "metadata-consumer-probe", Duration.ofHours(24));
    }

    @Override
    public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        if (!CONFIGURATION_PHASE.equals(state.phase())) {
            throw new IllegalArgumentException("Metadata consumer case is not waiting for its probe");
        }
        if (event instanceof CaseEvent.ConfigConfirmed) return new CaseStep.Finish(evaluate(context));
        if (event instanceof CaseEvent.ConfigUnavailable unavailable) {
            return new CaseStep.Finish(unavailable(unavailable));
        }
        if (event instanceof CaseEvent.TimedOut) {
            return new CaseStep.Finish(CaseOutcome.notVerified(
                    "metadata_consumer_probe_timeout", "metadata.consumer-probe.timeout"));
        }
        if (event instanceof CaseEvent.Aborted) {
            return new CaseStep.Finish(CaseOutcome.notVerified(
                    "metadata_consumer_probe_skipped", "metadata.consumer-probe.skipped"));
        }
        throw new IllegalArgumentException("Expected metadata consumer configuration completion");
    }

    private CaseOutcome evaluate(CaseContext context) {
        var entries = context.transcript().list(context.runId());
        var fetched = new LinkedHashSet<String>();
        var used = new LinkedHashSet<String>();
        var evidence = new ArrayList<EvidenceRef>();
        for (var entry : entries) {
            if (entry.direction() != Direction.INBOUND) continue;
            if ("MetadataFetch".equals(entry.samlSummary().get("type"))) {
                var variant = String.valueOf(entry.samlSummary().get("variant"));
                if (CONTROL.equals(variant) || variants.contains(variant)) {
                    fetched.add(variant);
                    evidence.add(new EvidenceRef("transcript", "transcript:" + entry.id()));
                }
            }
            if (entry.decodedSamlBytes() > 0 && entry.url() != null) {
                for (var variant : union(CONTROL, variants)) {
                    if (entry.url().contains("mdv=" + variant)
                            && entry.url().contains("run=" + context.runId())) {
                        used.add(variant);
                        evidence.add(new EvidenceRef("transcript", "transcript:" + entry.id()));
                    }
                }
            }
        }
        var details = Map.<String, Object>of(
                "required_variants", variants,
                "fetched_variants", List.copyOf(fetched),
                "used_variants", List.copyOf(used));
        if (!fetched.contains(CONTROL) || !used.contains(CONTROL)
                || !fetched.containsAll(variants)) {
            return new CaseOutcome(
                    Outcome.NOT_VERIFIED, "metadata_consumer_probe_incomplete",
                    "metadata.consumer-probe.incomplete", "metadata.consumer-probe.incomplete",
                    distinct(evidence), details);
        }
        return switch (rule) {
            case PERMITTED_IDENTITY_TRANSFORM -> new CaseOutcome(
                    Outcome.SATISFIED_WITH_NOTE, null,
                    used.containsAll(variants)
                            ? "metadata.unauthorized-transform.safely-accepted"
                            : "metadata.unauthorized-transform.rejected",
                    "metadata.unauthorized-transform.choice-recorded", distinct(evidence), details);
            case EXCLUDED_CONTENT -> new CaseOutcome(
                    variants.stream().anyMatch(used::contains) ? Outcome.VIOLATED : Outcome.SATISFIED,
                    null,
                    variants.stream().anyMatch(used::contains)
                            ? "metadata.excluded-content.accepted"
                            : "metadata.excluded-content.rejected",
                    variants.stream().anyMatch(used::contains)
                            ? "metadata.excluded-content.accepted"
                            : "metadata.excluded-content.rejected",
                    distinct(evidence), details);
            case OMITTED_KEY_INFO -> new CaseOutcome(
                    used.containsAll(variants) ? Outcome.SATISFIED : Outcome.VIOLATED,
                    null,
                    used.containsAll(variants)
                            ? "metadata.key-info-omission.accepted"
                            : "metadata.key-info-omission.rejected",
                    used.containsAll(variants)
                            ? "metadata.key-info-omission.accepted"
                            : "metadata.key-info-omission.rejected",
                    distinct(evidence), details);
        };
    }

    private CaseOutcome unavailable(CaseEvent.ConfigUnavailable event) {
        return new CaseOutcome(
                Outcome.NOT_VERIFIED, "metadata_consumer_probe_unavailable",
                "metadata.consumer-probe.unavailable", "metadata.consumer-probe.unavailable",
                List.of(), Map.of(
                        "configuration_issue", event.issue().name().toLowerCase(java.util.Locale.ROOT),
                        "configuration_note", event.note()));
    }

    private static String instruction(Rule rule, List<String> variants) {
        return "Configure the target to consume the Suite metadata for this Run. First load "
                + "`/p/<plan-id>/metadata?variant=control&run=<run-id>` and complete one SSO flow. "
                + "Then, for each variant " + variants + ", load the same URL with that `variant` value and "
                + "attempt the same SSO flow. Confirm only after every attempt has completed. Samlier determines "
                + "the outcome from recorded metadata fetches and variant-correlated inbound SAML; do not enter "
                + "an expected verdict. Probe rule: " + rule.name().toLowerCase(java.util.Locale.ROOT) + ".";
    }

    private static List<String> union(String first, List<String> rest) {
        var result = new ArrayList<String>();
        result.add(first);
        result.addAll(rest);
        return result;
    }

    private static List<EvidenceRef> distinct(List<EvidenceRef> evidence) {
        return evidence.stream().distinct().toList();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
