package org.samlier.api;

import io.javalin.config.JavalinConfig;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import org.samlier.runner.access.ManagementSessionExecutor;

/** Exchanges a fragment-delivered management secret for an HttpOnly cookie. */
public final class ManagementSessionRoutes {
    public static final String COOKIE_NAME = "__Host-samlier-management";
    private ManagementSessionRoutes() {}

    public static void register(JavalinConfig javalin, URI publicBase, ManagementSessionExecutor sessions) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(publicBase, "publicBase");
        Objects.requireNonNull(sessions, "sessions");
        javalin.routes.post("/api/manage/session", ctx -> {
            if (ctx.queryParam("t") != null || ctx.queryParam("token") != null) {
                ctx.status(400).json(Map.of("error", "token_in_query"));
                return;
            }
            if (!origin(publicBase).equals(ctx.header("Origin"))) {
                ctx.status(403).json(Map.of("error", "invalid_origin"));
                return;
            }
            var request = ctx.bodyAsClass(SessionWrite.class);
            if (request == null) throw new IllegalArgumentException("JSON body is required");
            var session = sessions.exchange(request.runId(), request.token());
            ctx.header("Set-Cookie", COOKIE_NAME + "=" + session.sessionToken()
                    + "; Path=/; Max-Age=28800; Secure; HttpOnly; SameSite=Strict");
            ctx.header("Cache-Control", "no-store");
            ctx.json(new SessionView(session.runId(), session.csrfToken()));
        });
    }

    private static String origin(URI value) {
        var port = value.getPort();
        return value.getScheme() + "://" + value.getHost() + (port < 0 ? "" : ":" + port);
    }

    record SessionWrite(String runId, String token) {}
    record SessionView(String runId, String csrfToken) {}
}
