package org.samlier.api;

import io.javalin.config.JavalinConfig;
import java.util.Objects;

final class EcpProbeRoutes {
    private EcpProbeRoutes() {}

    @FunctionalInterface
    interface Executor {
        java.util.List<org.samlier.runner.outbox.EcpProbeService.Result> execute(
                String runId, String username, String password);
    }

    static void register(JavalinConfig javalin, Executor executor) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(executor, "executor");
        javalin.routes.post("/api/runs/{id}/ecp-probe", ctx -> {
            var request = ctx.bodyAsClass(Request.class);
            if (request == null) throw new IllegalArgumentException("JSON body is required");
            ctx.json(executor.execute(ctx.pathParam("id"), request.username(), request.password()));
        });
    }

    record Request(String username, String password) {}
}
