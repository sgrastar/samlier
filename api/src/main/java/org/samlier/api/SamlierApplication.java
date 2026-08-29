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
import org.samlier.core.run.RunStatus;
import org.samlier.peer.idp.IdpPeerService;
import org.samlier.peer.sp.SpPeerService;
import org.samlier.runner.OutboundPolicy;
import org.samlier.runner.PreflightService;
import org.samlier.runner.RunEvent;
import org.samlier.runner.RunEventBus;
import org.samlier.runner.RunService;
import org.samlier.saml.crypto.FilePlanKeyStore;
import org.samlier.saml.crypto.XmlSigner;
import org.samlier.saml.metadata.MetadataService;
import org.samlier.saml.metadata.TargetMetadataParser;
import org.samlier.saml.normal.OpenSamlReader;
import org.samlier.saml.normal.SamlProtocolService;
import org.samlier.store.FileTranscriptRecorder;
import org.samlier.store.JsonCodec;
import org.samlier.store.MetadataCache;
import org.samlier.store.SqliteDatabase;
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
        var transcript = new FileTranscriptRecorder(database, json, config.dataDirectory());
        var metadataCache = new MetadataCache(config.dataDirectory());
        var eventBus = new RunEventBus();
        var runService = new RunService(plans, runs, eventBus, clock);
        var keyStore = new FilePlanKeyStore(config.dataDirectory(), clock);
        var signer = new XmlSigner();
        var metadataParser = new TargetMetadataParser();
        var saml = new SamlProtocolService(config.peerBaseUrl(), keyStore, signer, new OpenSamlReader(), clock);
        var metadata = new MetadataService(config.peerBaseUrl(), keyStore, signer, clock);
        var preflight = new PreflightService(config.publicBaseUrl(), plans, runs, runService, metadataCache,
                metadataParser, new OutboundPolicy(config.outboundAllowPrivate()), clock, json.mapper());
        var spPeer = new SpPeerService(plans, runs, runService, metadataCache, metadataParser, saml, transcript, clock);
        var idpPeer = new IdpPeerService(plans, runs, runService, metadataCache, metadataParser, saml, transcript, clock);
        var m1 = M1Runtime.create(
                config, database, json, plans, runs, transcript, transcript, metadataCache,
                metadataParser, keyStore, clock);

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
            ResultRoutes.register(javalin, m1::requireResult);
            if (config.mode() == AppConfig.Mode.HOSTED) {
                ManagementSessionRoutes.register(javalin, config.publicBaseUrl(), m1::exchange);
            }
            routes(javalin, config, plans, runs, transcript, eventBus, runService, preflight,
                    metadata, spPeer, idpPeer, m1, clock);
            javalin.routes.exception(MisdirectedRequest.class, (error, ctx) ->
                    ctx.status(421).json(new ApiModels.ErrorView("misdirected_request", error.getMessage())));
            javalin.routes.exception(IllegalArgumentException.class, (error, ctx) -> {
                ctx.status(HttpStatus.BAD_REQUEST).json(new ApiModels.ErrorView("invalid_request", error.getMessage()));
            });
            javalin.routes.exception(SecurityException.class, (error, ctx) ->
                    ctx.status(HttpStatus.FORBIDDEN)
                            .json(new ApiModels.ErrorView("access_denied", "Access denied")));
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
                               MetadataService metadata, SpPeerService spPeer, IdpPeerService idpPeer,
                               M1Runtime m1, Clock clock) {
        javalin.routes.get("/", SamlierApplication::serveIndex);
        javalin.routes.get("/reports/{run}", SamlierApplication::serveIndex);
        javalin.routes.get("/manage/{run}", SamlierApplication::serveIndex);
        javalin.routes.get("/assets/{file}", SamlierApplication::serveAsset);
        javalin.routes.get("/api/health", ctx -> ctx.json(Map.of(
                "status", "ok", "version", "0.1.0-SNAPSHOT", "mode", config.mode().name().toLowerCase())));
        javalin.routes.get("/api/plans", ctx -> ctx.json(plans.list().stream()
                .map(plan -> view(config, plan)).toList()));
        javalin.routes.post("/api/plans", ctx -> {
            var request = ctx.bodyAsClass(ApiModels.PlanWrite.class);
            var now = clock.instant();
            var plan = fromWrite(Identifiers.newId("plan"), request, now, now);
            plans.save(plan);
            ctx.status(HttpStatus.CREATED).json(view(config, plan));
        });
        javalin.routes.get("/api/plans/{id}", ctx -> ctx.json(view(config, requirePlan(plans, ctx.pathParam("id")))));
        javalin.routes.put("/api/plans/{id}", ctx -> {
            var existing = requirePlan(plans, ctx.pathParam("id"));
            var updated = fromWrite(existing.id(), ctx.bodyAsClass(ApiModels.PlanWrite.class),
                    existing.createdAt(), clock.instant());
            plans.save(updated);
            ctx.json(view(config, updated));
        });
        javalin.routes.delete("/api/plans/{id}", ctx -> {
            if (!plans.delete(ctx.pathParam("id"))) ctx.status(HttpStatus.NOT_FOUND);
            else ctx.status(HttpStatus.NO_CONTENT);
        });
        javalin.routes.get("/api/plans/{id}/runs", ctx -> ctx.json(runs.listForPlan(ctx.pathParam("id"))));
        javalin.routes.post("/api/plans/{id}/runs", ctx -> {
            var run = runService.create(ctx.pathParam("id"));
            ctx.status(HttpStatus.CREATED).json(new ApiModels.RunCreated(run, m1.issueManagementUrl(run)));
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
            ctx.contentType("application/samlmetadata+xml").result(metadata.generate(plan));
        });
        javalin.routes.get("/mdq/<entityId>", ctx -> {
            var entityId = URLDecoder.decode(ctx.pathParam("entityId"), StandardCharsets.UTF_8);
            var plan = plans.list().stream().filter(candidate -> peerEntityId(config, candidate).equals(entityId))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown entityID"));
            ctx.contentType("application/samlmetadata+xml").result(metadata.generate(plan));
        });
        javalin.routes.get("/p/{plan}/start/m0-roundtrip", ctx ->
                ctx.redirect(spPeer.start(ctx.pathParam("plan"), requiredQuery(ctx, "run")).toString()));
        javalin.routes.post("/p/{plan}/sp/acs/0", ctx -> {
            var summary = spPeer.consume(ctx.pathParam("plan"), ctx.bodyAsBytes(), headers(ctx), absoluteRequestUrl(ctx));
            ctx.contentType("text/html; charset=utf-8").result("<!doctype html><html lang=\"en\"><body>"
                    + "<h1>M0 SSO round trip completed</h1><p>Return to Samlier.</p><pre>"
                    + htmlEscape(summary.toString()) + "</pre></body></html>");
        });
        javalin.routes.get("/p/{plan}/idp/sso", ctx -> serveIdp(ctx, idpPeer));
        javalin.routes.post("/p/{plan}/idp/sso", ctx -> serveIdp(ctx, idpPeer));
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
        return new TestPlan(id, request.name(), request.profile(),
                new TestPlan.Target(request.targetKind(), request.targetEntityId(),
                        new TestPlan.MetadataSource(request.metadataSourceKind(), request.metadataSourceLocation())),
                request.suiteMetadataDelivery(), request.declaredFeatures(), request.parameters(), request.interaction(),
                createdAt, updatedAt);
    }

    private static ApiModels.PlanView view(AppConfig config, TestPlan plan) {
        var entityId = peerEntityId(config, plan);
        return new ApiModels.PlanView(plan, entityId, entityId + "/metadata",
                config.peerBaseUrl().resolve("/mdq/" + java.net.URLEncoder.encode(entityId, StandardCharsets.UTF_8)).toString());
    }

    private static String peerEntityId(AppConfig config, TestPlan plan) {
        return org.samlier.peer.PeerIdentity.primary(config.peerBaseUrl(), plan.id()).toString();
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
