package org.samlier.runner.cases;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.transcript.Direction;
import org.samlier.core.transcript.TranscriptContentReader;
import org.samlier.core.transcript.TranscriptRecorder;
import org.samlier.saml.raw.XmlDoctypeDetector;

/** Approved G2 case IIP-G03-a-{idp,sp}-01: passive inspection of target-generated SAML. */
public final class DtdFreeTargetSamlCase {
    public static final String OBLIGATION = "IIP-G03.a";

    public CaseOutcome evaluate(List<TargetSamlMessage> messages) {
        messages = List.copyOf(messages == null ? List.of() : messages);
        if (messages.isEmpty()) {
            return CaseOutcome.notVerified(
                    "no_target_generated_saml_messages",
                    "transcript.no-target-generated-saml");
        }

        var inspected = new ArrayList<String>();
        var violations = new ArrayList<EvidenceRef>();
        for (var message : messages) {
            inspected.add(message.evidenceRef());
            if (XmlDoctypeDetector.containsDoctype(message.xml())) {
                violations.add(new EvidenceRef("transcript", message.evidenceRef()));
            }
        }
        if (!violations.isEmpty()) {
            return new CaseOutcome(
                    Outcome.VIOLATED,
                    null,
                    "xml.dtd.target-emitted",
                    "case.iip-g03-a.dtd-target-emitted",
                    violations,
                    Map.of("inspected_messages", inspected.size(), "violating_messages", violations.size()));
        }
        return new CaseOutcome(
                Outcome.SATISFIED,
                null,
                "xml.dtd.absent",
                "case.iip-g03-a.dtd-absent",
                inspected.stream().map(value -> new EvidenceRef("transcript", value)).toList(),
                Map.of("inspected_messages", inspected.size()));
    }

    public CaseOutcome evaluateTranscript(
            String runId, TranscriptRecorder transcript, TranscriptContentReader content) {
        var messages = transcript.list(runId).stream()
                .filter(entry -> entry.direction() == Direction.INBOUND)
                .filter(entry -> entry.decodedSamlRef() != null && entry.decodedSamlBytes() > 0)
                .map(entry -> new TargetSamlMessage(
                        "transcript:" + entry.id(), content.readDecodedSaml(entry)))
                .toList();
        return evaluate(messages);
    }

    public record TargetSamlMessage(String evidenceRef, byte[] xml) {
        public TargetSamlMessage {
            if (evidenceRef == null || evidenceRef.isBlank()) {
                throw new IllegalArgumentException("evidenceRef must not be blank");
            }
            if (xml == null || xml.length == 0) throw new IllegalArgumentException("xml must not be empty");
            xml = xml.clone();
        }

        @Override
        public byte[] xml() {
            return xml.clone();
        }
    }
}
