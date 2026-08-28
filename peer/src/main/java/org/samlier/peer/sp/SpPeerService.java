package org.samlier.peer.sp;

import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.samlier.core.plan.PlanRepository;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.run.Reachability;
import org.samlier.core.run.RunRepository;
import org.samlier.core.run.RunStatus;
import org.samlier.core.transcript.Direction;
import org.samlier.core.transcript.TranscriptInput;
import org.samlier.core.transcript.TranscriptRecorder;
import org.samlier.runner.RunService;
import org.samlier.saml.metadata.MetadataService;
import org.samlier.saml.metadata.TargetMetadataParser;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SamlProtocolService;
import org.samlier.store.MetadataCache;

public final class SpPeerService {
    private final PlanRepository plans;
    private final RunRepository runs;
    private final RunService runService;
    private final MetadataCache metadataCache;
    private final TargetMetadataParser metadataParser;
    private final SamlProtocolService saml;
    private final TranscriptRecorder transcript;
    private final Clock clock;

    public SpPeerService(PlanRepository plans, RunRepository runs, RunService runService,
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

    public URI start(String planId, String runId) {
        var plan = plans.find(planId).orElseThrow(() -> new IllegalArgumentException("Unknown Test Plan"));
        if (plan.profile().role() != TargetRole.IDP) throw new IllegalArgumentException("This plan does not test an IdP");
        var run = runs.find(runId).orElseThrow(() -> new IllegalArgumentException("Unknown Run"));
        if (!run.planId().equals(planId)) throw new IllegalArgumentException("Run belongs to another Test Plan");
        var metadata = metadataParser.parse(metadataCache.get(plan.id()), plan.target().entityId());
        var destination = metadata.singleSignOnServices().stream()
                .filter(endpoint -> MetadataService.REDIRECT.equals(endpoint.binding()))
                .findFirst()
                .or(() -> metadata.singleSignOnServices().stream().findFirst())
                .orElseThrow(() -> new SamlException("Target metadata has no SingleSignOnService"))
                .location();
        var message = saml.buildAuthnRequest(plan, destination, run.id());
        var context = new LinkedHashMap<String, Object>(run.context());
        context.put("authnRequestId", message.id());
        context.put("authnRequestDestination", destination.toString());
        runService.update(run, RunStatus.WAITING_BROWSER, run.targetToSuiteReachability(), context);
        transcript.record(new TranscriptInput(run.id(), Direction.OUTBOUND, clock.instant(), message.id(), "GET",
                message.redirect().toString(), null, Map.of(), new byte[0], null,
                message.redirect().getRawQuery(), message.xml(), Map.of("type", "AuthnRequest", "id", message.id())));
        return message.redirect();
    }

    public Map<String, Object> consume(String planId, byte[] rawBody, Map<String, List<String>> headers, String requestUrl) {
        var message = saml.decodePost(rawBody, "SAMLResponse");
        var runId = message.relayState();
        if (runId == null) throw new SamlException("SAMLResponse has no RelayState correlation");
        var run = runs.find(runId).orElseThrow(() -> new SamlException("Unknown RelayState"));
        if (!run.planId().equals(planId)) throw new SamlException("RelayState belongs to another Test Plan");
        var expected = String.valueOf(run.context().getOrDefault("authnRequestId", ""));
        var actual = String.valueOf(message.parsed().summary().getOrDefault("inResponseTo", ""));
        transcript.record(new TranscriptInput(run.id(), Direction.INBOUND, clock.instant(), actual, "POST",
                requestUrl, 200, headers, rawBody, "application/x-www-form-urlencoded", null,
                message.xml(), message.parsed().summary()));
        if (expected.isBlank() || !expected.equals(actual)) {
            throw new SamlException("SAMLResponse InResponseTo does not match the active AuthnRequest");
        }
        var context = new LinkedHashMap<String, Object>(run.context());
        context.put("m0RoundTrip", "completed");
        context.put("responseSummary", message.parsed().summary());
        runService.update(run, RunStatus.COMPLETED, run.targetToSuiteReachability(), context);
        return message.parsed().summary();
    }
}
