package org.samlier.runner.cases;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.samlier.core.caseexec.ActionIds;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.InboundMatcher;
import org.samlier.core.caseexec.OutboundAction;
import org.samlier.core.caseexec.OutboundKind;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.TargetRole;
import org.samlier.saml.normal.SamlErrorProbeRequestFactory;
import org.samlier.saml.normal.SamlErrorProbeRequestFactory.Probe;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** Executes the three approved error-response probes through the persisted outbox. */
public final class IdpErrorResponseTestCase implements TestCase {
    public static final String CASE_ID = "IIP-IDP05-a-idp-01";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String SUCCESS = "urn:oasis:names:tc:SAML:2.0:status:Success";
    private static final String RESPONDER = "urn:oasis:names:tc:SAML:2.0:status:Responder";
    private static final List<Probe> PROBES = List.of(
            Probe.UNKNOWN_NAMEID_FORMAT,
            Probe.UNSATISFIABLE_AUTHN_CONTEXT,
            Probe.PASSIVE_WITHOUT_SESSION);
    private final IdpErrorProbeConfiguration configuration;
    private final SamlErrorProbeRequestFactory requests;

    public IdpErrorResponseTestCase(IdpErrorProbeConfiguration configuration) {
        this(configuration, new SamlErrorProbeRequestFactory());
    }

    IdpErrorResponseTestCase(
            IdpErrorProbeConfiguration configuration, SamlErrorProbeRequestFactory requests) {
        this.configuration = java.util.Objects.requireNonNull(configuration, "configuration");
        this.requests = java.util.Objects.requireNonNull(requests, "requests");
    }

    @Override public String id() { return CASE_ID; }
    @Override public TargetRole role() { return TargetRole.IDP; }

    @Override
    public CaseStep start(CaseContext context) {
        if (!configuration.preconditionsSatisfied()) {
            return new CaseStep.Finish(CaseOutcome.notVerified(
                    "error_response_preconditions_unmet", "idp.error-response.preconditions-unmet"));
        }
        return awaitProbe(context, 0, List.of(), List.of());
    }

    @Override
    public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        if (event instanceof CaseEvent.TimedOut) {
            return new CaseStep.Finish(CaseOutcome.notVerified(
                    "delivery_or_response_unknown", "idp.error-response.delivery-unknown"));
        }
        if (event instanceof CaseEvent.Aborted) {
            return new CaseStep.Finish(CaseOutcome.notVerified(
                    "probe_aborted", "idp.error-response.aborted"));
        }
        if (!(event instanceof CaseEvent.InboundMessage inbound)) {
            throw new IllegalArgumentException("IdP error probe requires an inbound SAML Response");
        }
        var index = integer(state, "probe_index");
        var requestId = string(state, "request_id");
        var violations = strings(state, "violations");
        var unverifiable = strings(state, "unverifiable");
        var result = inspect(PROBES.get(index), requestId, inbound.decodedSaml());
        if (result == ProbeResult.VIOLATION) violations.add(PROBES.get(index).name());
        if (result == ProbeResult.NOT_VERIFIED) unverifiable.add(PROBES.get(index).name());
        var evidence = strings(state, "evidence");
        evidence.add(inbound.evidence().reference());
        if (index + 1 < PROBES.size()) return awaitProbe(context, index + 1, violations, unverifiable, evidence);
        var refs = evidence.stream().map(ref -> new EvidenceRef("transcript", ref)).toList();
        if (!violations.isEmpty()) return new CaseStep.Finish(new CaseOutcome(
                Outcome.VIOLATED, null, "idp.error-response.violated", "case.idp.error-response.violated",
                refs, Map.of("violating_probes", violations, "unverifiable_probes", unverifiable)));
        if (!unverifiable.isEmpty()) return new CaseStep.Finish(new CaseOutcome(
                Outcome.NOT_VERIFIED, "error_response_not_conclusive", "idp.error-response.inconclusive",
                "case.idp.error-response.inconclusive", refs, Map.of("unverifiable_probes", unverifiable)));
        return new CaseStep.Finish(new CaseOutcome(
                Outcome.SATISFIED, null, "idp.error-response.satisfied", "case.idp.error-response.satisfied",
                refs, Map.of("completed_probes", PROBES.size())));
    }

    private CaseStep awaitProbe(
            CaseContext context, int index, List<String> violations, List<String> unverifiable) {
        return awaitProbe(context, index, violations, unverifiable, List.of());
    }

    private CaseStep awaitProbe(
            CaseContext context,
            int index,
            List<String> violations,
            List<String> unverifiable,
            List<String> evidence) {
        var phase = "await-" + PROBES.get(index).name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        var actionId = ActionIds.derive(context.runId(), id(), phase, 0);
        var requestId = "_" + actionId;
        var next = new CaseState(phase, Map.of(
                "probe_index", index,
                "request_id", requestId,
                "violations", List.copyOf(violations),
                "unverifiable", List.copyOf(unverifiable),
                "evidence", List.copyOf(evidence)));
        var payload = requests.build(
                PROBES.get(index), requestId, configuration.ssoEndpoint(), configuration.suiteIssuer(),
                configuration.registeredAcs(), context.clock().instant());
        var action = new OutboundAction(
                actionId, OutboundKind.AUTHN_REQUEST, payload, configuration.ssoEndpoint(), false);
        return new CaseStep.AwaitInbound(
                next,
                List.of(action),
                new InboundMatcher("saml-response", Map.of("InResponseTo", requestId)),
                configuration.responseTimeout());
    }

    private ProbeResult inspect(Probe probe, String requestId, byte[] responseXml) {
        try {
            var document = SecureXml.parse(responseXml);
            var root = document.getDocumentElement();
            if (!PROTOCOL.equals(root.getNamespaceURI()) || !"Response".equals(root.getLocalName())
                    || !requestId.equals(root.getAttribute("InResponseTo"))) return ProbeResult.NOT_VERIFIED;
            var statusCodes = root.getElementsByTagNameNS(PROTOCOL, "StatusCode");
            if (statusCodes.getLength() == 0) return ProbeResult.NOT_VERIFIED;
            var topLevel = ((Element) statusCodes.item(0)).getAttribute("Value");
            if (probe == Probe.UNSATISFIABLE_AUTHN_CONTEXT) {
                if (RESPONDER.equals(topLevel)) return ProbeResult.SATISFIED;
                if (SUCCESS.equals(topLevel)) {
                    var classRefs = root.getElementsByTagNameNS(
                            "urn:oasis:names:tc:SAML:2.0:assertion", "AuthnContextClassRef");
                    if (classRefs.getLength() > 0 && requests.unavailableAuthnContext(requestId)
                            .equals(classRefs.item(0).getTextContent())) return ProbeResult.NOT_VERIFIED;
                }
                return ProbeResult.VIOLATION;
            }
            return SUCCESS.equals(topLevel) ? ProbeResult.VIOLATION : ProbeResult.SATISFIED;
        } catch (SamlException malformed) {
            return ProbeResult.NOT_VERIFIED;
        }
    }

    private int integer(CaseState state, String key) {
        var value = state.data().get(key);
        if (!(value instanceof Number number)) throw new IllegalStateException("Missing numeric state: " + key);
        return number.intValue();
    }

    private String string(CaseState state, String key) {
        var value = state.data().get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalStateException("Missing text state: " + key);
        return text;
    }

    @SuppressWarnings("unchecked")
    private ArrayList<String> strings(CaseState state, String key) {
        var value = state.data().get(key);
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String))) {
            throw new IllegalStateException("Missing string-list state: " + key);
        }
        return new ArrayList<>((List<String>) list);
    }

    private enum ProbeResult { SATISFIED, VIOLATION, NOT_VERIFIED }
}
