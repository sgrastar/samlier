package org.samlier.api;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.PlanRepository;
import org.samlier.core.run.RunRepository;
import org.samlier.core.run.RunStatus;
import org.samlier.runner.outbox.EcpProbeService;
import org.samlier.saml.ecp.EcpProbeEnvelopeFactory;
import org.samlier.saml.metadata.MetadataService;
import org.samlier.saml.metadata.TargetMetadataParser;
import org.samlier.saml.normal.SamlProtocolService;
import org.samlier.store.MetadataCache;

/** Composes the non-evaluative ECP exchange used as evidence by approved passive M3 cases. */
final class EcpProbeRuntime {
    private final URI peerBase;
    private final PlanRepository plans;
    private final RunRepository runs;
    private final MetadataCache metadataCache;
    private final TargetMetadataParser metadataParser;
    private final SamlProtocolService saml;
    private final EcpProbeEnvelopeFactory envelopes;
    private final EcpProbeService probes;

    EcpProbeRuntime(
            URI peerBase,
            PlanRepository plans,
            RunRepository runs,
            MetadataCache metadataCache,
            TargetMetadataParser metadataParser,
            SamlProtocolService saml,
            EcpProbeEnvelopeFactory envelopes,
            EcpProbeService probes) {
        this.peerBase = Objects.requireNonNull(peerBase, "peerBase");
        this.plans = Objects.requireNonNull(plans, "plans");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.metadataCache = Objects.requireNonNull(metadataCache, "metadataCache");
        this.metadataParser = Objects.requireNonNull(metadataParser, "metadataParser");
        this.saml = Objects.requireNonNull(saml, "saml");
        this.envelopes = Objects.requireNonNull(envelopes, "envelopes");
        this.probes = Objects.requireNonNull(probes, "probes");
    }

    EcpProbeService.Result execute(String runId, String username, String password) {
        if (username == null || username.isBlank() || username.contains(":")) {
            throw new IllegalArgumentException("HTTP Basic username must be non-blank and must not contain ':'");
        }
        if (password == null || password.isEmpty()) throw new IllegalArgumentException("Password must not be empty");
        var run = runs.find(runId).orElseThrow(() -> new IllegalArgumentException("Unknown Run"));
        if (run.status() != RunStatus.COMPLETED) {
            throw new IllegalArgumentException("ECP probing requires a completed baseline SSO round trip");
        }
        var plan = plans.find(run.planId()).orElseThrow(() -> new IllegalStateException("Run has no Test Plan"));
        if (plan.profile() != PlanProfile.IDP_FULL) {
            throw new IllegalArgumentException("The ECP probe is available only for an IdP Full Profile Run");
        }
        var target = metadataParser.parse(metadataCache.get(plan.id()), plan.target().entityId());
        var endpoint = target.singleSignOnServices().stream()
                .filter(value -> MetadataService.SOAP.equals(value.binding()))
                .map(org.samlier.saml.metadata.TargetMetadata.Endpoint::location)
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Target metadata has no SOAP SingleSignOnService for ECP"));
        var responseConsumer = URI.create(peerBase.resolve("/p/" + plan.id() + "/sp/paos")
                + "?run=" + java.net.URLEncoder.encode(run.id(), StandardCharsets.UTF_8));
        var request = saml.buildEcpAuthnRequest(plan, endpoint, responseConsumer, "ecp-" + run.id());
        var envelope = envelopes.baseline(request.xml());
        var userBytes = username.getBytes(StandardCharsets.UTF_8);
        var passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        var credential = new byte[userBytes.length + 1 + passwordBytes.length];
        System.arraycopy(userBytes, 0, credential, 0, userBytes.length);
        credential[userBytes.length] = ':';
        System.arraycopy(passwordBytes, 0, credential, userBytes.length + 1, passwordBytes.length);
        try {
            return probes.execute(run.id(), endpoint, envelope, credential);
        } finally {
            Arrays.fill(userBytes, (byte) 0);
            Arrays.fill(passwordBytes, (byte) 0);
            Arrays.fill(credential, (byte) 0);
            Arrays.fill(envelope, (byte) 0);
        }
    }
}
