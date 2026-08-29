package org.samlier.api;

import io.javalin.config.JavalinConfig;
import java.util.Objects;
import org.samlier.runner.result.ResultArtifactQuery;

/** Isolated public-result route pending the protected composition-root update. */
public final class ResultRoutes {
    private ResultRoutes() {}

    public static void register(JavalinConfig javalin, ResultArtifactQuery results) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(results, "results");
        javalin.routes.get("/api/runs/{id}/result.json", ctx -> {
            var bytes = results.require(ctx.pathParam("id"));
            ctx.contentType("application/json; charset=utf-8");
            ctx.header("Cache-Control", "no-store");
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.result(bytes);
        });
    }
}
