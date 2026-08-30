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
import org.samlier.runner.ActiveProbeCorrelation;
import org.samlier.core.evaluation.EvidenceRef;
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
    private final ActiveProbeResponseHandler activeProbeResponses;

    public SpPeerService(PlanRepository plans, RunRepository runs, RunService runService,
                         MetadataCache metadataCache, TargetMetadataParser metadataParser,
                         SamlProtocolService saml, TranscriptRecorder transcript, Clock clock) {
        this(plans, runs, runService, metadataCache, metadataParser, saml, transcript, clock,
                (runId, actionId, decodedSaml, evidence) -> { });
    }

    public SpPeerService(PlanRepository plans, RunRepository runs, RunService runService,
                         MetadataCache metadataCache, TargetMetadataParser metadataParser,
                         SamlProtocolService saml, TranscriptRecorder transcript, Clock clock,
                         ActiveProbeResponseHandler activeProbeResponses) {
        this.plans = plans;
        this.runs = runs;
        this.runService = runService;
        this.metadataCache = metadataCache;
        this.metadataParser = metadataParser;
        this.saml = saml;
        this.transcript = transcript;
        this.clock = clock;
        this.activeProbeResponses = java.util.Objects.requireNonNull(
                activeProbeResponses, "activeProbeResponses");
    }

    public URI start(String planId, String runId) {
        var plan = plans.find(planId).orElseThrow(() -> new IllegalArgumentException("Unknown Test Plan"));
        if (plan.profile().role() != TargetRole.IDP) throw new IllegalArgumentException("This plan does not test an IdP");
        var run = runs.find(runId).orElseThrow(() -> new IllegalArgumentException("Unknown Run"));
        if (!run.planId().equals(planId)) throw new IllegalArgumentException("Run belongs to another Test Plan");
        var metadata = metadataParser.parse(
                metadataCache.getRunSnapshot(run.id(), plan.id()), plan.target().entityId());
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
        return consumeDetailed(planId, rawBody, headers, requestUrl).summary();
    }

    public ConsumeResult consumeDetailed(
            String planId, byte[] rawBody, Map<String, List<String>> headers, String requestUrl) {
        var rawMessage = saml.decodePostRaw(rawBody, "SAMLResponse");
        var variant = queryParameter(requestUrl, "mdv");
        var correlatedRun = queryParameter(requestUrl, "run");
        var metadataProbe = variant != null && correlatedRun != null;
        var activeProbe = ActiveProbeCorrelation.parse(rawMessage.relayState());
        var runId = activeProbe.map(ActiveProbeCorrelation.Value::runId)
                .orElse(metadataProbe ? correlatedRun : rawMessage.relayState());
        if (runId == null) throw new SamlException("SAMLResponse has no RelayState correlation");
        var run = runs.find(runId).orElseThrow(() -> new SamlException("Unknown RelayState"));
        if (!run.planId().equals(planId)) throw new SamlException("RelayState belongs to another Test Plan");
        var transcriptCorrelation = activeProbe.map(ActiveProbeCorrelation.Value::actionId).orElse(run.id());
        var transcriptEntry = transcript.record(new TranscriptInput(run.id(), Direction.INBOUND, clock.instant(), transcriptCorrelation, "POST",
                requestUrl, 200, headers, rawBody, "application/x-www-form-urlencoded", null,
                rawMessage.xml(), Map.of("type", "SAMLResponse", "parseStatus", "not-yet-parsed")));
        org.samlier.saml.normal.SamlProtocolService.DecodedMessage message;
        try {
            message = saml.parse(rawMessage);
        } catch (SamlException malformed) {
            if (activeProbe.isEmpty()) throw malformed;
            var summary = Map.<String, Object>of(
                    "parseStatus", "error",
                    "errorCategory", "malformed-saml-response");
            transcript.updateSamlAnalysis(transcriptEntry.id(), transcriptCorrelation, summary);
            activeProbeResponses.accept(
                    run.id(), activeProbe.orElseThrow().actionId(), rawMessage.xml(),
                    new EvidenceRef("transcript", transcriptEntry.id()));
            return new ConsumeResult(
                    summary,
                    activeProbe.orElseThrow().runId(),
                    activeProbe.orElseThrow().actionId());
        }
        var expected = String.valueOf(run.context().getOrDefault("authnRequestId", ""));
        var actual = String.valueOf(message.parsed().summary().getOrDefault("inResponseTo", ""));
        var analyzedSummary = new LinkedHashMap<String, Object>(message.parsed().summary());
        if (!metadataProbe && activeProbe.isEmpty()) {
            analyzedSummary.put("normalFlowAccepted", !expected.isBlank() && expected.equals(actual));
        }
        transcript.updateSamlAnalysis(transcriptEntry.id(), actual, analyzedSummary);
        if (!metadataProbe && activeProbe.isEmpty() && (expected.isBlank() || !expected.equals(actual))) {
            throw new SamlException("SAMLResponse InResponseTo does not match the active AuthnRequest");
        }
        if (activeProbe.isPresent()) {
            activeProbeResponses.accept(
                    run.id(), activeProbe.orElseThrow().actionId(), rawMessage.xml(),
                    new EvidenceRef("transcript", transcriptEntry.id()));
        } else if (!metadataProbe) {
            var context = new LinkedHashMap<String, Object>(run.context());
            context.put("m0RoundTrip", "completed");
            context.put("responseSummary", message.parsed().summary());
            runService.update(run, RunStatus.COMPLETED, run.targetToSuiteReachability(), context);
        }
        return new ConsumeResult(
                analyzedSummary,
                activeProbe.map(ActiveProbeCorrelation.Value::runId).orElse(null),
                activeProbe.map(ActiveProbeCorrelation.Value::actionId).orElse(null));
    }

    private String queryParameter(String requestUrl, String name) {
        var query = URI.create(requestUrl).getRawQuery();
        if (query == null) return null;
        for (var part : query.split("&")) {
            var separator = part.indexOf('=');
            var key = separator < 0 ? part : part.substring(0, separator);
            if (name.equals(java.net.URLDecoder.decode(key, java.nio.charset.StandardCharsets.UTF_8))) {
                return separator < 0 ? "" : java.net.URLDecoder.decode(
                        part.substring(separator + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    @FunctionalInterface
    public interface ActiveProbeResponseHandler {
        void accept(String runId, String actionId, byte[] decodedSaml, EvidenceRef evidence);
    }

    public record ConsumeResult(
            Map<String, Object> summary,
            String activeProbeRunId,
            String activeProbeActionId) {
        public ConsumeResult { summary = Map.copyOf(summary); }
        public boolean activeProbe() { return activeProbeRunId != null; }
    }
}
