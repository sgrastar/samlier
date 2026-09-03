package com.samlscope.runner.cases;

import java.util.ArrayList;
import java.util.List;
import com.samlscope.core.evaluation.CaseOutcome;
import com.samlscope.core.evaluation.EvidenceRef;
import com.samlscope.core.evaluation.Outcome;
import com.samlscope.saml.normal.SamlException;
import com.samlscope.saml.normal.SecureXml;
import org.w3c.dom.Document;

final class PassiveXmlCaseSupport {
    private PassiveXmlCaseSupport() {}

    static CaseOutcome evaluate(
            List<TargetTranscriptMessages.Message> messages,
            String ruleCode,
            DocumentRule rule) {
        messages = List.copyOf(messages == null ? List.of() : messages);
        if (messages.isEmpty()) {
            return CaseOutcome.notVerified(
                    "no_target_generated_saml_messages",
                    "transcript.no-target-generated-saml");
        }
        var violations = new ArrayList<Violation>();
        var inspected = new ArrayList<EvidenceRef>();
        var unparseable = new ArrayList<EvidenceRef>();
        var observedFields = 0;
        for (var message : messages) {
            var evidence = new EvidenceRef("transcript", message.evidenceRef());
            try {
                var result = rule.inspect(SecureXml.parse(message.xml()));
                inspected.add(evidence);
                observedFields += result.observedFields();
                for (var field : result.violatingFields()) {
                    violations.add(new Violation(evidence, field));
                }
                for (var field : result.unverifiableFields()) {
                    unparseable.add(new EvidenceRef("transcript-field", message.evidenceRef() + "#" + field));
                }
            } catch (SamlException malformed) {
                unparseable.add(evidence);
            }
        }
        if (!violations.isEmpty()) {
            return new CaseOutcome(
                    Outcome.VIOLATED,
                    null,
                    ruleCode + ".violated",
                    "case." + ruleCode + ".violated",
                    violations.stream().map(Violation::evidence).distinct().toList(),
                    java.util.Map.of(
                            "inspected_messages", inspected.size(),
                            "observed_fields", observedFields,
                            "violating_fields", violations.stream().map(Violation::field).toList(),
                            "unparseable_messages", unparseable.size()));
        }
        if (!unparseable.isEmpty()) {
            return new CaseOutcome(
                    Outcome.NOT_VERIFIED,
                    "target_message_or_field_unparseable",
                    ruleCode + ".unparseable",
                    "case." + ruleCode + ".unparseable",
                    unparseable,
                    java.util.Map.of(
                            "inspected_messages", inspected.size(),
                            "observed_fields", observedFields,
                            "unparseable_messages", unparseable.size()));
        }
        var outcome = observedFields == 0 ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED;
        return new CaseOutcome(
                outcome,
                null,
                observedFields == 0 ? ruleCode + ".no-relevant-fields" : ruleCode + ".satisfied",
                observedFields == 0 ? "case." + ruleCode + ".no-relevant-fields" : "case." + ruleCode + ".satisfied",
                inspected,
                java.util.Map.of("inspected_messages", inspected.size(), "observed_fields", observedFields));
    }

    interface DocumentRule {
        Inspection inspect(Document document);
    }

    record Inspection(int observedFields, List<String> violatingFields, List<String> unverifiableFields) {
        Inspection(int observedFields, List<String> violatingFields) {
            this(observedFields, violatingFields, List.of());
        }

        Inspection {
            if (observedFields < 0) throw new IllegalArgumentException("observedFields must not be negative");
            violatingFields = List.copyOf(violatingFields);
            unverifiableFields = List.copyOf(unverifiableFields);
        }
    }

    private record Violation(EvidenceRef evidence, String field) {}
}
