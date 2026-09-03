package com.samlscope.runner.cases;

import java.util.ArrayList;
import java.util.List;
import com.samlscope.core.caseexec.CaseExecutionRepository;
import com.samlscope.core.caseexec.CaseExecutionStatus;
import com.samlscope.core.evaluation.CaseOutcome;
import com.samlscope.core.evaluation.EvidenceRef;
import com.samlscope.core.evaluation.Outcome;
import com.samlscope.saml.normal.SamlException;
import com.samlscope.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** Relates emitted AuthnRequest versions to the target's proven normal-flow response support. */
public final class SamlRequesterVersionCase {
    public static final String NORMAL_SSO_CASE_ID = "IIP-SSO01-a-sp-01";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private final CaseExecutionRepository executions;

    public SamlRequesterVersionCase(CaseExecutionRepository executions) {
        this.executions = java.util.Objects.requireNonNull(executions, "executions");
    }

    public CaseOutcome evaluate(String runId, List<TargetTranscriptMessages.Message> messages) {
        messages = List.copyOf(messages == null ? List.of() : messages);
        if (messages.isEmpty()) return CaseOutcome.notVerified(
                "no_target_generated_saml_messages", "transcript.no-target-generated-saml");
        var inspected = new ArrayList<EvidenceRef>();
        var violations = new ArrayList<String>();
        var unparseable = new ArrayList<EvidenceRef>();
        var requests = 0;
        for (var message : messages) {
            try {
                var document = SecureXml.parse(message.xml());
                var nodes = document.getElementsByTagNameNS(PROTOCOL, "AuthnRequest");
                for (var index = 0; index < nodes.getLength(); index++) {
                    requests++;
                    var element = (Element) nodes.item(index);
                    var evidence = new EvidenceRef("transcript", message.evidenceRef() + "#AuthnRequest[" + index + "]");
                    inspected.add(evidence);
                    if (!"2.0".equals(element.getAttribute("Version"))) {
                        violations.add(evidence.reference() + ":Version=" + element.getAttribute("Version"));
                    }
                }
            } catch (SamlException malformed) {
                unparseable.add(new EvidenceRef("transcript", message.evidenceRef()));
            }
        }
        if (!violations.isEmpty()) return new CaseOutcome(
                Outcome.VIOLATED, null, "saml.requester-version.violated", "case.saml.requester-version.violated",
                inspected, java.util.Map.of("observed_requests", requests, "violations", violations));
        if (!unparseable.isEmpty()) return new CaseOutcome(
                Outcome.NOT_VERIFIED, "target_request_unparseable", "saml.requester-version.unparseable",
                "case.saml.requester-version.unparseable", unparseable,
                java.util.Map.of("observed_requests", requests));
        if (requests == 0) return CaseOutcome.notVerified(
                "no_target_generated_authnrequest", "saml.requester-version.no-request");
        var normal = executions.find(runId, NORMAL_SSO_CASE_ID);
        if (normal.isEmpty() || normal.orElseThrow().status() != CaseExecutionStatus.FINISHED) {
            return CaseOutcome.notVerified("normal_sso_not_completed", "saml.requester-version.normal-sso-not-completed");
        }
        var normalOutcome = normal.orElseThrow().outcome();
        if (normalOutcome.outcome() == Outcome.SATISFIED || normalOutcome.outcome() == Outcome.SATISFIED_WITH_NOTE) {
            return new CaseOutcome(
                    Outcome.SATISFIED, null, "saml.requester-version.satisfied", "case.saml.requester-version.satisfied",
                    combine(inspected, normalOutcome.evidence()), java.util.Map.of("observed_requests", requests));
        }
        if (normalOutcome.outcome() == Outcome.VIOLATED) {
            return new CaseOutcome(
                    Outcome.VIOLATED, null, "saml.requester-version.response-not-processed",
                    "case.saml.requester-version.response-not-processed",
                    combine(inspected, normalOutcome.evidence()), java.util.Map.of("observed_requests", requests));
        }
        return CaseOutcome.notVerified(
                "normal_sso_result_not_conclusive", "saml.requester-version.normal-sso-inconclusive");
    }

    private List<EvidenceRef> combine(List<EvidenceRef> first, List<EvidenceRef> second) {
        var combined = new ArrayList<EvidenceRef>(first);
        combined.addAll(second);
        return List.copyOf(combined);
    }
}
