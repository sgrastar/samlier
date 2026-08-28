package org.samlier.peer.idp;

import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.samlier.core.plan.PlanRepository;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.run.RunRepository;
import org.samlier.core.run.RunStatus;
import org.samlier.core.run.TestRun;
import org.samlier.core.transcript.Direction;
import org.samlier.core.transcript.TranscriptInput;
import org.samlier.core.transcript.TranscriptRecorder;
import org.samlier.runner.RunService;
import org.samlier.saml.metadata.TargetMetadataParser;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SamlProtocolService;
import org.samlier.store.MetadataCache;

public final class IdpPeerService {
    private final PlanRepository plans;
    private final RunRepository runs;
    private final RunService runService;
    private final MetadataCache metadataCache;
    private final TargetMetadataParser metadataParser;
    private final SamlProtocolService saml;
    private final TranscriptRecorder transcript;
    private final Clock clock;

    public IdpPeerService(PlanRepository plans, RunRepository runs, RunService runService,
                          MetadataCache metadataCache, TargetMetadataParser metadataParser,
                          SamlProtocolService saml, TranscriptRecorder transcript, Clock clock) {
        this.plans = plans;
        this.runs = runs;
        this.runService = runService;
        this.metadataCache = metadataCache;
        this.metadataParser = metadataParser;
        this.saml = saml;
        this.transcript = transcript;
        this.clock = clock;
    }

    public SamlProtocolService.ResponseMessage consume(
            String planId, String method, String rawQuery, byte[] rawBody,
            Map<String, List<String>> headers, String requestUrl) {
        var plan = plans.find(planId).orElseThrow(() -> new IllegalArgumentException("Unknown Test Plan"));
        if (plan.profile().role() != TargetRole.SP) throw new IllegalArgumentException("This plan does not test an SP");
        var run = activeRun(planId);
        var request = "GET".equalsIgnoreCase(method)
                ? saml.decodeRedirect(rawQuery, "SAMLRequest")
                : saml.decodePost(rawBody, "SAMLRequest");
        var requestRoot = request.parsed().document().getDocumentElement();
        var metadata = metadataParser.parse(metadataCache.get(plan.id()), plan.target().entityId());
        var requestedAcs = requestRoot.getAttribute("AssertionConsumerServiceURL");
        URI acs;
        if (!requestedAcs.isBlank()) {
            acs = URI.create(requestedAcs);
            var registered = metadata.assertionConsumerServices().stream()
                    .anyMatch(endpoint -> endpoint.location().equals(acs));
            if (!registered) throw new SamlException("AuthnRequest ACS is not registered in target metadata");
        } else {
            acs = metadata.assertionConsumerServices().stream()
                    .filter(TargetMetadataParserEndpoint::isPreferred)
                    .findFirst()
                    .or(() -> metadata.assertionConsumerServices().stream().findFirst())
                    .orElseThrow(() -> new SamlException("Target metadata has no AssertionConsumerService"))
                    .location();
        }
        transcript.record(new TranscriptInput(run.id(), Direction.INBOUND, clock.instant(),
                requestRoot.getAttribute("ID"), method, requestUrl, 200, headers, rawBody,
                "GET".equalsIgnoreCase(method) ? null : "application/x-www-form-urlencoded",
                rawQuery, request.xml(), request.parsed().summary()));
        var response = saml.buildResponse(plan, request, acs, "samlier-m0-user");
        transcript.record(new TranscriptInput(run.id(), Direction.OUTBOUND, clock.instant(), response.id(), "POST",
                acs.toString(), null, Map.of(), new byte[0], "application/x-www-form-urlencoded", null,
                response.xml(), Map.of("type", "Response", "id", response.id(), "destination", acs.toString())));
        var context = new LinkedHashMap<String, Object>(run.context());
        context.put("m0RoundTrip", "response-issued");
        context.put("requestSummary", request.parsed().summary());
        runService.update(run, RunStatus.COMPLETED, run.targetToSuiteReachability(), context);
        return response;
    }

    private TestRun activeRun(String planId) {
        return runs.listForPlan(planId).stream()
                .filter(run -> run.status() != RunStatus.COMPLETED && run.status() != RunStatus.ABORTED)
                .findFirst()
                .orElseThrow(() -> new SamlException("Create a Run before starting login at the target SP"));
    }

    private static final class TargetMetadataParserEndpoint {
        private static boolean isPreferred(org.samlier.saml.metadata.TargetMetadata.Endpoint endpoint) {
            return endpoint.isDefault() || Integer.valueOf(0).equals(endpoint.index());
        }
    }
}
