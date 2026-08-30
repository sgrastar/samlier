package org.samlier.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.samlier.core.Identifiers;
import org.samlier.core.plan.PlanRepository;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.run.RunRepository;
import org.samlier.peer.idp.IdpPeerService;
import org.samlier.peer.sp.SpPeerService;
import org.samlier.peer.logout.SloPeerService;
import org.samlier.runner.OutboundPolicy;
import org.samlier.runner.outbox.EcpProbeService;
import org.samlier.runner.outbox.HttpOutboundSender;
import org.samlier.runner.outbox.InMemoryEphemeralCredentialProvider;
import org.samlier.runner.outbox.OutboundDispatcher;
import org.samlier.runner.PreflightService;
import org.samlier.runner.RunEvent;
import org.samlier.runner.RunEventBus;
import org.samlier.runner.RunService;
import org.samlier.runner.TranscriptAutomationRecorder;
import org.samlier.runner.MetadataLabService;
import org.samlier.saml.crypto.FilePlanKeyStore;
import org.samlier.saml.crypto.XmlSigner;
import org.samlier.saml.ecp.EcpProbeEnvelopeFactory;
import org.samlier.saml.metadata.MetadataService;
import org.samlier.saml.metadata.TargetMetadataParser;
import org.samlier.saml.normal.OpenSamlReader;
import org.samlier.saml.normal.SamlProtocolService;
import org.samlier.store.FileTranscriptRecorder;
import org.samlier.store.JsonCodec;
import org.samlier.store.MetadataCache;
import org.samlier.store.SqliteDatabase;
import org.samlier.store.SqliteCaseExecutionRepository;
import org.samlier.store.SqlitePlanRepository;
import org.samlier.store.SqliteRunRepository;

public final class SamlierApplication {
    private static final SecureRandom NONCE_RANDOM = new SecureRandom();

    private SamlierApplication() {}

    public static void main(String[] args) {
        var config = AppConfig.fromEnvironment();
        create(config).start(config.httpPort());
    }

    public static Javalin create(AppConfig config) {
        var clock = Clock.systemUTC();
        var json = new JsonCodec();
        var database = new SqliteDatabase(config.dataDirectory());
        PlanRepository plans = new SqlitePlanRepository(database, json);
        RunRepository runs = new SqliteRunRepository(database, json);
        var storedTranscript = new FileTranscriptRecorder(database, json, config.dataDirectory());
        var transcript = new TranscriptAutomationRecorder(storedTranscript, storedTranscript);
        var metadataCache = new MetadataCache(config.dataDirectory());
        var eventBus = new RunEventBus();
        var runService = new RunService(plans, runs, eventBus, clock);
        var metadataLab = new MetadataLabService(config.peerBaseUrl(), plans, runs, runService, clock);
        var keyStore = new FilePlanKeyStore(config.dataDirectory(), clock);
        var signer = new XmlSigner();
        var metadataParser = new TargetMetadataParser();
        var saml = new SamlProtocolService(config.peerBaseUrl(), keyStore, signer, new OpenSamlReader(), clock);
        var metadata = new MetadataService(config.peerBaseUrl(), keyStore, signer, clock);
        var preflight = new PreflightService(config.publicBaseUrl(), plans, runs, runService, metadataCache,
                metadataParser, new OutboundPolicy(config.outboundAllowPrivate()), clock, json.mapper());
        var idpPeer = new IdpPeerService(plans, runs, runService, metadataCache, metadataParser, saml, transcript, clock);
        var secondaryIdpPeer = new IdpPeerService(
                plans, runs, runService, metadataCache, metadataParser, saml, transcript, clock, true);
        var sloPeer = new SloPeerService(plans, runs, metadataCache, metadataParser, saml, transcript, clock);
        var caseExecutions = new SqliteCaseExecutionRepository(database, json);
        var ephemeralCredentials = new InMemoryEphemeralCredentialProvider();
        var outboundDispatcher = new OutboundDispatcher(
                caseExecutions, HttpOutboundSender.create(transcript, clock), ephemeralCredentials,
                new OutboundPolicy(config.outboundAllowPrivate()), clock);
        outboundDispatcher.recoverAfterRestart();
        var ecpProbe = new EcpProbeRuntime(
                config.peerBaseUrl(), plans, runs, metadataCache, metadataParser, saml,
                new EcpProbeEnvelopeFactory(),
                new EcpProbeService(caseExecutions, ephemeralCredentials, outboundDispatcher, clock));
        var m1 = M1Runtime.create(
                config, database, json, plans, runs, transcript, transcript, metadataCache,
                metadataParser, keyStore, caseExecutions, metadataLab, outboundDispatcher, clock);
        transcript.onRecorded(m1::reconcileTranscriptEvidence);
        var spPeer = new SpPeerService(
                plans, runs, runService, metadataCache, metadataParser, saml, transcript, clock,
                m1::acceptActiveProbe);
        var hostedRateLimiter = new HostedRateLimiter(clock);
        var hostedRunProvisioner = new org.samlier.store.SqliteHostedRunProvisioner(database, json);

        return Javalin.create(javalin -> {
            javalin.startup.showJavalinBanner = false;
            javalin.http.maxRequestSize = 6L * 1024 * 1024;
            javalin.jsonMapper(new JavalinJackson().updateMapper(mapper -> {
                mapper.findAndRegisterModules();
                mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            }));
            javalin.routes.before(ctx -> {
                securityHeaders(ctx);
                enforceConfiguredOrigin(ctx, config);
            });
            QuickCheckRoutes.register(javalin, m1::quickCheck);
            ResultRoutes.register(javalin, m1::requireResult, m1::requireReport);
            PublicationRoutes.register(javalin, m1::publish);
            InteractionRoutes.register(javalin, m1::pending);
            BootstrapContractRoutes.register(javalin, m1::bootstrapContracts);
            MetadataLabRoutes.register(javalin, metadataLab);
            ProtocolEvidenceRoutes.register(
                    javalin, m1::protocolEvidence, m1::evaluateProtocolEvidence);
            AttestationRoutes.register(javalin, m1::attest);
            ConfigurationRoutes.register(javalin, m1::configure);
            BrowserCompletionRoutes.register(javalin, m1::completeBrowser);
            MilestoneRoutes.register(javalin, m1::startMilestone);
            EcpProbeRoutes.register(javalin, ecpProbe::execute);
            javalin.routes.get("/api/runs/{id}/active-probe", ctx ->
                    ctx.json(m1.activeProbeStatus(ctx.pathParam("id"))));
            if (config.mode() == AppConfig.Mode.HOSTED) {
                ManagementSessionRoutes.register(javalin, config.publicBaseUrl(), m1::exchange);
                javalin.routes.before("/api/plans/{id}", ctx -> {
                    if (ctx.method().name().equals("GET")) {
                        m1.authorizePlan(
                                ctx.pathParam("id"), ctx.cookie(ManagementSessionRoutes.COOKIE_NAME));
                    } else {
                        m1.authorizePlanMutation(
                                ctx.pathParam("id"),
                                ctx.cookie(ManagementSessionRoutes.COOKIE_NAME),
                                ctx.header("X-CSRF-Token"));
                    }
                });
                javalin.routes.before("/api/plans/{id}/runs", ctx -> {
                    if (ctx.method().name().equals("GET")) {
                        m1.authorizePlan(
                                ctx.pathParam("id"), ctx.cookie(ManagementSessionRoutes.COOKIE_NAME));
                    } else {
                        m1.authorizePlanMutation(
                                ctx.pathParam("id"),
                                ctx.cookie(ManagementSessionRoutes.COOKIE_NAME),
                                ctx.header("X-CSRF-Token"));
                    }
                });
                javalin.routes.before("/api/runs/{id}", ctx ->
                        m1.authorize(ctx.pathParam("id"), ctx.cookie(ManagementSessionRoutes.COOKIE_NAME)));
                javalin.routes.before("/api/runs/{id}/preflight", ctx ->
                        m1.authorizeMutation(
                                ctx.pathParam("id"),
                                ctx.cookie(ManagementSessionRoutes.COOKIE_NAME),
                                ctx.header("X-CSRF-Token")));
                javalin.routes.before("/api/runs/{id}/events", ctx ->
                        m1.authorize(ctx.pathParam("id"), ctx.cookie(ManagementSessionRoutes.COOKIE_NAME)));
                javalin.routes.before("/api/runs/{id}/quick-check", ctx ->
                        m1.authorizeMutation(
                                ctx.pathParam("id"),
                                ctx.cookie(ManagementSessionRoutes.COOKIE_NAME),
                                ctx.header("X-CSRF-Token")));
                javalin.routes.before("/api/runs/{id}/active-probe", ctx ->
                        m1.authorize(ctx.pathParam("id"), ctx.cookie(ManagementSessionRoutes.COOKIE_NAME)));
                javalin.routes.before("/api/runs/{id}/interactions", ctx ->
                        m1.authorize(ctx.pathParam("id"), ctx.cookie(ManagementSessionRoutes.COOKIE_NAME)));
                javalin.routes.before("/api/runs/{id}/bootstrap-contracts", ctx ->
                        m1.authorize(ctx.pathParam("id"), ctx.cookie(ManagementSessionRoutes.COOKIE_NAME)));
                javalin.routes.before("/api/runs/{id}/metadata-lab", ctx ->
                        m1.authorize(ctx.pathParam("id"), ctx.cookie(ManagementSessionRoutes.COOKIE_NAME)));
                javalin.routes.before("/api/runs/{id}/metadata-lab/variant", ctx ->
                        m1.authorizeMutation(
                                ctx.pathParam("id"),
                                ctx.cookie(ManagementSessionRoutes.COOKIE_NAME),
                                ctx.header("X-CSRF-Token")));
                javalin.routes.before("/api/runs/{id}/protocol-evidence", ctx ->
                        m1.authorize(ctx.pathParam("id"), ctx.cookie(ManagementSessionRoutes.COOKIE_NAME)));
                javalin.routes.before("/api/runs/{id}/protocol-evidence/evaluate", ctx ->
                        m1.authorizeMutation(
                                ctx.pathParam("id"),
                                ctx.cookie(ManagementSessionRoutes.COOKIE_NAME),
                                ctx.header("X-CSRF-Token")));
                javalin.routes.before("/api/runs/{id}/cases/{caseId}/attest", ctx ->
                        m1.authorizeMutation(
                                ctx.pathParam("id"),
                                ctx.cookie(ManagementSessionRoutes.COOKIE_NAME),
                                ctx.header("X-CSRF-Token")));
                javalin.routes.before("/api/runs/{id}/cases/{caseId}/configure", ctx ->
                        m1.authorizeMutation(
                                ctx.pathParam("id"),
                                ctx.cookie(ManagementSessionRoutes.COOKIE_NAME),
                                ctx.header("X-CSRF-Token")));
                javalin.routes.before("/api/runs/{id}/cases/{caseId}/browser-complete", ctx ->
                        m1.authorizeMutation(
                                ctx.pathParam("id"),
                                ctx.cookie(ManagementSessionRoutes.COOKIE_NAME),
                                ctx.header("X-CSRF-Token")));
                javalin.routes.before("/api/runs/{id}/milestones/{milestone}/start", ctx ->
                        m1.authorizeMutation(
                                ctx.pathParam("id"),
                                ctx.cookie(ManagementSessionRoutes.COOKIE_NAME),
                                ctx.header("X-CSRF-Token")));
                javalin.routes.before("/api/runs/{id}/ecp-probe", ctx ->
                        m1.authorizeMutation(
                                ctx.pathParam("id"),
                                ctx.cookie(ManagementSessionRoutes.COOKIE_NAME),
                                ctx.header("X-CSRF-Token")));
                javalin.routes.before("/api/runs/{id}/publish", ctx ->
                        m1.authorizeMutation(
                                ctx.pathParam("id"),
                                ctx.cookie(ManagementSessionRoutes.COOKIE_NAME),
                                ctx.header("X-CSRF-Token")));
                for (var path : List.of(
                        "/api/runs/{id}/result.json", "/api/runs/{id}/report.html")) {
                    javalin.routes.before(path, ctx -> {
                        if (!m1.isPublished(ctx.pathParam("id"))) {
                            m1.authorize(ctx.pathParam("id"), ctx.cookie(ManagementSessionRoutes.COOKIE_NAME));
                        }
                    });
                }
                javalin.routes.before("/api/runs/{id}/transcript", ctx ->
                        m1.authorize(ctx.pathParam("id"), ctx.cookie(ManagementSessionRoutes.COOKIE_NAME)));
            }
            routes(javalin, config, plans, runs, transcript, eventBus, runService, preflight,
                    metadata, metadataLab, spPeer, idpPeer, secondaryIdpPeer, sloPeer, m1,
                    hostedRateLimiter, hostedRunProvisioner, clock);
            javalin.routes.exception(MisdirectedRequest.class, (error, ctx) ->
                    ctx.status(421).json(new ApiModels.ErrorView("misdirected_request", error.getMessage())));
            javalin.routes.exception(IllegalArgumentException.class, (error, ctx) -> {
                ctx.status(HttpStatus.BAD_REQUEST).json(new ApiModels.ErrorView("invalid_request", error.getMessage()));
            });
            javalin.routes.exception(SecurityException.class, (error, ctx) ->
                    ctx.status(HttpStatus.FORBIDDEN)
                            .json(new ApiModels.ErrorView("access_denied", "Access denied")));
            javalin.routes.exception(HostedRateLimiter.RateLimitExceeded.class, (error, ctx) ->
                    ctx.status(HttpStatus.TOO_MANY_REQUESTS)
                            .json(new ApiModels.ErrorView("rate_limited", error.getMessage())));
            javalin.routes.exception(Exception.class, (error, ctx) -> {
                error.printStackTrace();
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .json(new ApiModels.ErrorView("internal_error", "The request could not be completed"));
            });
        });
    }

    private static void routes(io.javalin.config.JavalinConfig javalin, AppConfig config,
                               PlanRepository plans, RunRepository runs,
                               org.samlier.core.transcript.TranscriptRecorder transcript,
                               RunEventBus eventBus, RunService runService, PreflightService preflight,
                               MetadataService metadata, MetadataLabService metadataLab,
                               SpPeerService spPeer, IdpPeerService idpPeer,
                               IdpPeerService secondaryIdpPeer,
                               SloPeerService sloPeer,
                               M1Runtime m1, HostedRateLimiter hostedRateLimiter,
                               org.samlier.store.SqliteHostedRunProvisioner hostedRunProvisioner,
                               Clock clock) {
        javalin.routes.get("/", SamlierApplication::serveIndex);
        javalin.routes.get("/reports/{run}", SamlierApplication::serveIndex);
        javalin.routes.get("/manage/{run}", SamlierApplication::serveIndex);
        javalin.routes.get("/browser/{run}/{caseId}", SamlierApplication::serveIndex);
        javalin.routes.get("/assets/{file}", SamlierApplication::serveAsset);
        javalin.routes.get("/api/health", ctx -> ctx.json(Map.of(
                "status", "ok", "version", "0.1.0", "mode", config.mode().name().toLowerCase())));
        javalin.routes.get("/api/plans", ctx -> ctx.json((config.mode() == AppConfig.Mode.HOSTED
                        ? m1.authorizedPlans(ctx.cookie(ManagementSessionRoutes.COOKIE_NAME))
                        : plans.list()).stream()
                .map(plan -> view(config, plan)).toList()));
        javalin.routes.post("/api/plans", ctx -> {
            if (config.mode() == AppConfig.Mode.HOSTED) {
                hostedRateLimiter.requireAllowed("create-plan", ctx.ip(), 10, Duration.ofHours(1));
            }
            var request = ctx.bodyAsClass(ApiModels.PlanWrite.class);
            var now = clock.instant();
            var plan = fromWrite(Identifiers.newId("plan"), request, now, now);
            ApiModels.RunCreated initialRun = null;
            if (config.mode() == AppConfig.Mode.HOSTED) {
                var run = runService.prepare(plan.id());
                var access = m1.prepareManagementAccess(run);
                if (!hostedRunProvisioner.createPlanWithInitialRun(plan, run, access.grant())) {
                    throw new HostedRateLimiter.RateLimitExceeded(
                            "Another Run against this target is already active");
                }
                runService.publishCreated(run);
                initialRun = new ApiModels.RunCreated(run, access.access().managementUrl().toString());
            } else {
                plans.save(plan);
            }
            ctx.status(HttpStatus.CREATED).json(new ApiModels.PlanCreated(view(config, plan), initialRun));
        });
        javalin.routes.get("/api/plans/{id}", ctx -> ctx.json(view(config, requirePlan(plans, ctx.pathParam("id")))));
        javalin.routes.put("/api/plans/{id}", ctx -> {
            var existing = requirePlan(plans, ctx.pathParam("id"));
            var updated = fromWrite(existing.id(), ctx.bodyAsClass(ApiModels.PlanWrite.class),
                    existing.createdAt(), clock.instant());
            if (config.mode() == AppConfig.Mode.HOSTED) {
                if (!hostedRunProvisioner.updatePlanUnlessActiveRetarget(updated)) {
                    ctx.status(HttpStatus.CONFLICT).json(new ApiModels.ErrorView(
                            "active_run_conflict", "A Plan with an active Run cannot change target entity ID"));
                    return;
                }
            } else {
                plans.save(updated);
            }
            ctx.json(view(config, updated));
        });
        javalin.routes.delete("/api/plans/{id}", ctx -> {
            if (!plans.delete(ctx.pathParam("id"))) ctx.status(HttpStatus.NOT_FOUND);
            else ctx.status(HttpStatus.NO_CONTENT);
        });
        javalin.routes.get("/api/plans/{id}/runs", ctx -> ctx.json(runs.listForPlan(ctx.pathParam("id"))));
        javalin.routes.post("/api/plans/{id}/runs", ctx -> {
            var requestedPlan = requirePlan(plans, ctx.pathParam("id"));
            org.samlier.core.run.TestRun run;
            String managementUrl;
            if (config.mode() == AppConfig.Mode.HOSTED) {
                hostedRateLimiter.requireAllowed("create-run", ctx.ip(), 20, Duration.ofHours(1));
                run = runService.prepare(requestedPlan.id());
                var access = m1.prepareManagementAccess(run);
                if (!hostedRunProvisioner.createRun(run, access.grant())) {
                    throw new HostedRateLimiter.RateLimitExceeded(
                            "Another Run against this target is already active");
                }
                runService.publishCreated(run);
                managementUrl = access.access().managementUrl().toString();
            } else {
                run = runService.create(requestedPlan.id());
                managementUrl = null;
            }
            ctx.status(HttpStatus.CREATED).json(new ApiModels.RunCreated(run, managementUrl));
        });
        javalin.routes.get("/api/runs/{id}", ctx -> ctx.json(requireRun(runs, ctx.pathParam("id"))));
        javalin.routes.post("/api/runs/{id}/preflight", ctx -> ctx.json(preflight.execute(ctx.pathParam("id"))));
        javalin.routes.get("/api/runs/{id}/transcript", ctx -> ctx.json(transcript.list(ctx.pathParam("id"))));
        javalin.routes.sse("/api/runs/{id}/events", client -> {
            var runId = client.ctx().pathParam("id");
            var run = requireRun(runs, runId);
            client.sendEvent("run", new RunEvent(runId, "run.snapshot", clock.instant(),
                    Map.of("status", run.status().name(), "reachability", run.targetToSuiteReachability().name())));
            client.keepAlive();
            var subscription = eventBus.subscribe(runId, event -> client.sendEvent("run", event));
            client.onClose(subscription::close);
        });

        javalin.routes.get("/p/{plan}/metadata", ctx -> {
            var plan = requirePlan(plans, ctx.pathParam("plan"));
            confirmReachabilityProbe(ctx.queryParam("probe"), plan.id(), runs, runService);
            var variant = MetadataService.Variant.parse(ctx.queryParam("variant"));
            var runId = ctx.queryParam("run");
            if (variant != MetadataService.Variant.BASELINE) {
                runId = requiredQuery(ctx, "run");
                var run = requireRun(runs, runId);
                if (!plan.id().equals(run.planId())) {
                    throw new IllegalArgumentException("Run belongs to another Test Plan");
                }
                transcript.record(new org.samlier.core.transcript.TranscriptInput(
                        run.id(), org.samlier.core.transcript.Direction.INBOUND, clock.instant(),
                        "metadata:" + variant.id(), "GET", absoluteRequestUrl(ctx), 200,
                        headers(ctx), new byte[0], null, ctx.req().getQueryString(), new byte[0],
                        Map.of("type", "MetadataFetch", "variant", variant.id())));
                ctx.header("Cache-Control", "no-store");
            }
            ctx.contentType("application/samlmetadata+xml").result(metadata.generate(plan, variant, runId));
        });
        javalin.routes.get("/p/{plan}/metadata/live", ctx -> {
            var plan = requirePlan(plans, ctx.pathParam("plan"));
            var runId = requiredQuery(ctx, "run");
            var variant = metadataLab.selected(runId, plan.id());
            var run = requireRun(runs, runId);
            var redirectStatus = metadataRedirectStatus(variant);
            transcript.record(new org.samlier.core.transcript.TranscriptInput(
                    run.id(), org.samlier.core.transcript.Direction.INBOUND, clock.instant(),
                    "metadata-live:" + variant.id(), "GET", absoluteRequestUrl(ctx),
                    redirectStatus == null ? 200 : redirectStatus.getCode(),
                    headers(ctx), new byte[0], null, ctx.req().getQueryString(), new byte[0],
                    Map.of("type", "MetadataFetch", "variant", variant.id(), "feed", "live")));
            ctx.header("Cache-Control", "no-store");
            if (redirectStatus != null) {
                var location = config.peerBaseUrl().resolve(
                        "/p/" + plan.id() + "/metadata/live/content?run=" + run.id()
                                + "&variant=" + variant.id());
                ctx.redirect(location.toString(), redirectStatus);
                return;
            }
            ctx.contentType("application/samlmetadata+xml")
                    .result(metadata.generate(plan, variant, runId));
        });
        javalin.routes.get("/p/{plan}/metadata/live/content", ctx -> {
            var plan = requirePlan(plans, ctx.pathParam("plan"));
            var runId = requiredQuery(ctx, "run");
            var run = requireRun(runs, runId);
            if (!plan.id().equals(run.planId())) {
                throw new IllegalArgumentException("Run belongs to another Test Plan");
            }
            var variant = MetadataService.Variant.parse(requiredQuery(ctx, "variant"));
            if (metadataRedirectStatus(variant) == null) {
                throw new IllegalArgumentException("Metadata content route requires a redirect fixture");
            }
            ctx.header("Cache-Control", "no-store");
            ctx.contentType("application/samlmetadata+xml")
                    .result(metadata.generate(plan, variant, runId));
        });
        javalin.routes.get("/mdq/<entityId>", ctx -> {
            var entityId = URLDecoder.decode(ctx.pathParam("entityId"), StandardCharsets.UTF_8);
            var plan = plans.list().stream().filter(candidate ->
                            peerEntityId(config, candidate).equals(entityId)
                                    || secondaryIdpEntityId(config, candidate).equals(entityId))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown entityID"));
            var payload = secondaryIdpEntityId(config, plan).equals(entityId)
                    ? metadata.generateSecondaryIdp(plan)
                    : metadata.generate(plan);
            ctx.contentType("application/samlmetadata+xml").result(payload);
        });
        javalin.routes.get("/p/{plan}/idp/secondary/metadata", ctx -> {
            var plan = requirePlan(plans, ctx.pathParam("plan"));
            ctx.contentType("application/samlmetadata+xml").result(metadata.generateSecondaryIdp(plan));
        });
        javalin.routes.get("/p/{plan}/start/m0-roundtrip", ctx ->
                ctx.redirect(spPeer.start(ctx.pathParam("plan"), requiredQuery(ctx, "run")).toString()));
        javalin.routes.get("/p/{plan}/probe/{action}", ctx -> {
            var runId = requiredQuery(ctx, "run");
            var status = m1.activeProbeStatus(runId);
            requireActiveProbeRoute(ctx, status);
            ctx.header("Cache-Control", "no-store");
            ctx.header("Content-Security-Policy", "default-src 'none'; form-action 'self'; "
                    + "frame-ancestors 'none'; base-uri 'none'; object-src 'none'");
            ctx.contentType("text/html; charset=utf-8").result(activeProbeStartPage(status));
        });
        javalin.routes.post("/p/{plan}/probe/{action}", ctx -> {
            var runId = requiredQuery(ctx, "run");
            var status = m1.activeProbeStatus(runId);
            requireActiveProbeRoute(ctx, status);
            var fresh = "true".equals(ctx.formParam("freshSessionConfirmed"));
            renderActiveProbe(ctx, m1.prepareActiveProbe(
                    runId, ctx.pathParam("action"), fresh));
        });
        javalin.routes.post("/p/{plan}/sp/acs/0", ctx -> {
            var consumed = spPeer.consumeDetailed(
                    ctx.pathParam("plan"), ctx.bodyAsBytes(), headers(ctx), absoluteRequestUrl(ctx));
            if (consumed.activeProbe()) {
                var status = m1.activeProbeStatus(consumed.activeProbeRunId());
                if (status.state() == org.samlier.runner.ActiveProbeCoordinator.State.READY) {
                    renderActiveProbe(ctx, m1.prepareActiveProbe(
                            consumed.activeProbeRunId(), status.actionId(), false));
                    return;
                }
                ctx.header("Cache-Control", "no-store").contentType("text/html; charset=utf-8")
                        .result("<!doctype html><html lang=\"en\"><body><h1>Active probes completed</h1>"
                                + "<p>The responses were recorded and evaluated automatically.</p></body></html>");
                return;
            }
            var summary = consumed.summary();
            ctx.contentType("text/html; charset=utf-8").result("<!doctype html><html lang=\"en\"><body>"
                    + "<h1>M0 SSO round trip completed</h1><p>Return to Samlier.</p><pre>"
                    + htmlEscape(summary.toString()) + "</pre></body></html>");
        });
        javalin.routes.get("/p/{plan}/idp/sso", ctx -> serveIdp(ctx, idpPeer));
        javalin.routes.post("/p/{plan}/idp/sso", ctx -> serveIdp(ctx, idpPeer));
        javalin.routes.get("/p/{plan}/idp/secondary/sso", ctx -> serveIdp(ctx, secondaryIdpPeer));
        javalin.routes.post("/p/{plan}/idp/secondary/sso", ctx -> serveIdp(ctx, secondaryIdpPeer));
        for (var role : List.of("sp", "idp")) {
            javalin.routes.get("/p/{plan}/" + role + "/slo", ctx ->
                    serveSlo(ctx, sloPeer, SloPeerService.Transport.FRONT_CHANNEL));
            javalin.routes.post("/p/{plan}/" + role + "/slo", ctx ->
                    serveSlo(ctx, sloPeer, SloPeerService.Transport.FRONT_CHANNEL));
            javalin.routes.post("/p/{plan}/" + role + "/slo/soap", ctx ->
                    serveSlo(ctx, sloPeer, SloPeerService.Transport.SOAP));
        }
        javalin.routes.post("/p/{plan}/sp/paos", ctx -> {
            var runId = requiredQuery(ctx, "run");
            var run = requireRun(runs, runId);
            if (!ctx.pathParam("plan").equals(run.planId())) {
                throw new IllegalArgumentException("Run belongs to another Test Plan");
            }
            transcript.record(new org.samlier.core.transcript.TranscriptInput(
                    run.id(), org.samlier.core.transcript.Direction.INBOUND, clock.instant(),
                    "ecp-response-consumer", "POST", absoluteRequestUrl(ctx), 204,
                    headers(ctx), ctx.bodyAsBytes(), ctx.contentType(), ctx.req().getQueryString(),
                    ctx.bodyAsBytes(), Map.of("type", "EcpPaosResponse")));
            ctx.status(HttpStatus.NO_CONTENT);
        });
    }

    private static void requireActiveProbeRoute(
            Context ctx, org.samlier.runner.ActiveProbeCoordinator.Status status) {
        if (!ctx.pathParam("plan").equals(status.planId())
                || status.state() != org.samlier.runner.ActiveProbeCoordinator.State.READY
                || !ctx.pathParam("action").equals(status.actionId())) {
            throw new IllegalArgumentException("Active probe route does not match the ready action");
        }
    }

    private static String activeProbeStartPage(
            org.samlier.runner.ActiveProbeCoordinator.Status status) {
        var fresh = status.requiresFreshSession()
                ? "<p>This first probe verifies IsPassive with no existing IdP session. Open this page in a new private browser window, then confirm below.</p>"
                        + "<label><input required type=\"checkbox\" name=\"freshSessionConfirmed\" value=\"true\"> "
                        + "This browser context has no active target IdP session.</label>"
                : "";
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Active SAML probes</title></head>"
                + "<body><h1>Run active SAML probes</h1>"
                + "<p>Samlier will send one positive control and the three approved abnormal AuthnRequest fixtures in sequence, then evaluate each correlated Response.</p>"
                + "<form method=\"post\">" + fresh
                + "<p><button type=\"submit\">Start active probes</button></p></form></body></html>";
    }

    private static void renderActiveProbe(
            Context ctx, org.samlier.runner.ActiveProbeCoordinator.PreparedProbe probe) {
        var nonceBytes = new byte[18];
        NONCE_RANDOM.nextBytes(nonceBytes);
        var nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        ctx.header("Content-Security-Policy", "default-src 'none'; script-src 'nonce-" + nonce
                        + "'; form-action " + origin(probe.destination())
                        + "; frame-ancestors 'none'; base-uri 'none'; object-src 'none'");
        ctx.header("Cache-Control", "no-store").contentType("text/html; charset=utf-8")
                .result(HtmlPostPage.renderRequest(
                        probe.destination(), probe.samlRequest(), probe.relayState(), nonce));
    }

    private static void serveSlo(Context ctx, SloPeerService service, SloPeerService.Transport transport) {
        var result = service.consume(
                ctx.pathParam("plan"), transport, ctx.method().name(), ctx.req().getQueryString(),
                ctx.bodyAsBytes(), headers(ctx), absoluteRequestUrl(ctx));
        if (result.response() == null) {
            ctx.status(HttpStatus.NO_CONTENT);
            return;
        }
        if (transport == SloPeerService.Transport.SOAP) {
            ctx.contentType("text/xml; charset=utf-8").result(service.soapResponse(result));
            return;
        }
        if (MetadataService.REDIRECT.equals(result.responseBinding())) {
            ctx.redirect(service.redirectResponse(result).toString());
            return;
        }
        var nonceBytes = new byte[18];
        NONCE_RANDOM.nextBytes(nonceBytes);
        var nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        ctx.header("Content-Security-Policy", "default-src 'none'; script-src 'nonce-" + nonce
                        + "'; form-action " + origin(result.response().destination())
                        + "; frame-ancestors 'none'; base-uri 'none'; object-src 'none'");
        ctx.header("Cache-Control", "no-store").contentType("text/html; charset=utf-8")
                .result(HtmlPostPage.render(
                        result.response().destination(), result.response().base64(),
                        result.response().relayState(), nonce).replace("SAMLResponse", "SAMLResponse"));
    }

    private static void serveIdp(Context ctx, IdpPeerService service) {
        var response = service.consume(ctx.pathParam("plan"), ctx.method().name(), ctx.req().getQueryString(),
                ctx.bodyAsBytes(), headers(ctx), absoluteRequestUrl(ctx));
        var nonceBytes = new byte[18];
        NONCE_RANDOM.nextBytes(nonceBytes);
        var nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        var destinationOrigin = origin(response.destination());
        ctx.header("Content-Security-Policy", "default-src 'none'; script-src 'nonce-" + nonce
                        + "'; form-action " + destinationOrigin
                        + "; frame-ancestors 'none'; base-uri 'none'; object-src 'none'");
        ctx.header("Cache-Control", "no-store").contentType("text/html; charset=utf-8")
                .result(HtmlPostPage.render(response.destination(), response.base64(), response.relayState(), nonce));
    }

    private static void confirmReachabilityProbe(String runId, String planId, RunRepository runs, RunService runService) {
        if (runId == null) return;
        var run = requireRun(runs, runId);
        if (!run.planId().equals(planId)) throw new IllegalArgumentException("Reachability probe belongs to another plan");
        var context = new LinkedHashMap<String, Object>(run.context());
        context.put("reachabilityConfirmedBy", "metadata-probe");
        runService.update(run, run.status(), Reachability.CONFIRMED, context);
    }

    private static TestPlan fromWrite(String id, ApiModels.PlanWrite request,
                                      java.time.Instant createdAt, java.time.Instant updatedAt) {
        if (request == null) throw new IllegalArgumentException("JSON body is required");
        if (!request.authorizedTarget()) {
            throw new IllegalArgumentException(
                    "Confirm that you own or are authorized to test the target before creating the Test Plan");
        }
        return new TestPlan(id, request.name(), request.profile(),
                new TestPlan.Target(request.targetKind(), request.targetEntityId(),
                        new TestPlan.MetadataSource(request.metadataSourceKind(), request.metadataSourceLocation())),
                request.suiteMetadataDelivery(), request.declaredFeatures(), request.parameters(), request.interaction(),
                createdAt, updatedAt);
    }

    private static ApiModels.PlanView view(AppConfig config, TestPlan plan) {
        var entityId = peerEntityId(config, plan);
        var secondaryEntityId = secondaryIdpEntityId(config, plan);
        var summary = new ApiModels.PlanSummary(
                plan.id(), plan.name(), plan.profile(),
                new ApiModels.TargetSummary(plan.target().kind(), plan.target().entityId()));
        return new ApiModels.PlanView(summary, entityId, entityId + "/metadata",
                config.peerBaseUrl().resolve("/mdq/" + java.net.URLEncoder.encode(entityId, StandardCharsets.UTF_8)).toString(),
                secondaryEntityId, secondaryEntityId + "/metadata");
    }

    private static String peerEntityId(AppConfig config, TestPlan plan) {
        return org.samlier.peer.PeerIdentity.primary(config.peerBaseUrl(), plan.id()).toString();
    }

    private static String secondaryIdpEntityId(AppConfig config, TestPlan plan) {
        return org.samlier.peer.PeerIdentity.secondaryIdp(config.peerBaseUrl(), plan.id()).toString();
    }

    private static TestPlan requirePlan(PlanRepository plans, String id) {
        return plans.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown Test Plan"));
    }

    private static org.samlier.core.run.TestRun requireRun(RunRepository runs, String id) {
        return runs.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown Run"));
    }

    private static String requiredQuery(Context ctx, String name) {
        var value = ctx.queryParam(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing query parameter " + name);
        return value;
    }

    private static Map<String, List<String>> headers(Context ctx) {
        var result = new LinkedHashMap<String, List<String>>();
        var names = ctx.req().getHeaderNames();
        while (names.hasMoreElements()) {
            var name = names.nextElement();
            result.put(name, java.util.Collections.list(ctx.req().getHeaders(name)));
        }
        return result;
    }

    private static String absoluteRequestUrl(Context ctx) {
        var query = ctx.req().getQueryString();
        return ctx.url() + (query == null ? "" : "?" + query);
    }

    private static HttpStatus metadataRedirectStatus(MetadataService.Variant variant) {
        return switch (variant) {
            case REDIRECT_301 -> HttpStatus.MOVED_PERMANENTLY;
            case REDIRECT_302 -> HttpStatus.FOUND;
            case REDIRECT_307 -> HttpStatus.TEMPORARY_REDIRECT;
            default -> null;
        };
    }

    private static void securityHeaders(Context ctx) {
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.header("Referrer-Policy", "no-referrer");
        ctx.header("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        ctx.header("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self'; "
                + "connect-src 'self'; img-src 'self' data:; form-action 'self'; "
                + "frame-ancestors 'none'; base-uri 'none'; object-src 'none'");
    }

    private static void enforceConfiguredOrigin(Context ctx, AppConfig config) {
        if (config.mode() != AppConfig.Mode.HOSTED) return;
        var peerRoute = ctx.path().startsWith("/p/") || ctx.path().startsWith("/mdq/");
        var expected = peerRoute ? config.peerBaseUrl() : config.publicBaseUrl();
        var host = ctx.header("Host");
        if (host == null || !authorityMatches(host, expected)) {
            throw new MisdirectedRequest("This endpoint is not available on the requested origin");
        }
    }

    private static boolean authorityMatches(String hostHeader, URI expected) {
        try {
            var actual = URI.create(expected.getScheme() + "://" + hostHeader);
            return expected.getHost().equalsIgnoreCase(actual.getHost())
                    && effectivePort(expected) == effectivePort(actual);
        } catch (IllegalArgumentException invalidHost) {
            return false;
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static void serveIndex(Context ctx) {
        serveClasspath(ctx, "/public/index.html", "text/html; charset=utf-8");
    }

    private static void serveAsset(Context ctx) {
        var file = ctx.pathParam("file");
        if (!file.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("Invalid asset name");
        var contentType = file.endsWith(".js") ? "text/javascript; charset=utf-8"
                : file.endsWith(".css") ? "text/css; charset=utf-8" : "application/octet-stream";
        serveClasspath(ctx, "/public/assets/" + file, contentType);
    }

    private static void serveClasspath(Context ctx, String name, String contentType) {
        try (var stream = SamlierApplication.class.getResourceAsStream(name)) {
            if (stream == null) {
                ctx.status(HttpStatus.NOT_FOUND);
                return;
            }
            ctx.contentType(contentType).result(stream.readAllBytes());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not serve application asset", e);
        }
    }

    private static String origin(URI uri) {
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("SAML destination must be an absolute HTTP(S) URL");
        }
        var scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("SAML destination must use HTTP or HTTPS");
        }
        var host = uri.getHost().contains(":") ? "[" + uri.getHost() + "]" : uri.getHost();
        var authority = host + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        return scheme + "://" + authority;
    }

    private static String htmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class MisdirectedRequest extends RuntimeException {
        private MisdirectedRequest(String message) { super(message); }
    }
}
