package com.samlscope.runner.cases;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.samlscope.core.evaluation.CaseOutcome;
import com.samlscope.core.evaluation.EvidenceRef;
import com.samlscope.core.evaluation.Outcome;
import com.samlscope.core.transcript.TranscriptContentReader;
import com.samlscope.core.transcript.TranscriptRecorder;
import com.samlscope.saml.raw.XmlDoctypeDetector;

/** Approved G2 case IIP-G03-a-{idp,sp}-01: passive inspection of target-generated SAML. */
public final class DtdFreeTargetSamlCase {
    public static final String OBLIGATION = "IIP-G03.a";

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
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
        return evaluate(TargetTranscriptMessages.read(runId, transcript, content));
    }
}
