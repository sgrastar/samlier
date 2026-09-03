package com.samlscope.api;

import io.javalin.config.JavalinConfig;
import java.util.Objects;
import com.samlscope.runner.result.ResultArtifactQuery;
import com.samlscope.runner.result.ReportArtifactQuery;

/** Isolated public-result route pending the protected composition-root update. */
public final class ResultRoutes {
    private ResultRoutes() {}

    public static void register(JavalinConfig javalin, ResultArtifactQuery results) {
        register(javalin, results, runId -> { throw new IllegalArgumentException("Report artifact is unavailable"); });
    }

    public static void register(
            JavalinConfig javalin, ResultArtifactQuery results, ReportArtifactQuery reports) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(reports, "reports");
        javalin.routes.get("/api/runs/{id}/result.json", ctx -> {
            var bytes = results.require(ctx.pathParam("id"));
            ctx.contentType("application/json; charset=utf-8");
            ctx.header("Cache-Control", "no-store");
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.result(bytes);
        });
        javalin.routes.get("/api/runs/{id}/report.html", ctx -> {
            var bytes = reports.requireReport(ctx.pathParam("id"));
            ctx.contentType("text/html; charset=utf-8");
            ctx.header("Content-Disposition", "attachment; filename=\"samlscope-report.html\"");
            ctx.header("Cache-Control", "no-store");
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.result(bytes);
        });
    }
}
