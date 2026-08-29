package org.samlier.api;

import io.javalin.config.JavalinConfig;
import java.util.Objects;
import org.samlier.runner.QuickCheckExecutor;

/** Isolated route registration; the signed G2 application composition root remains untouched. */
public final class QuickCheckRoutes {
    private QuickCheckRoutes() {}

    public static void register(JavalinConfig javalin, QuickCheckExecutor quickCheck) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(quickCheck, "quickCheck");
        javalin.routes.post("/api/runs/{id}/quick-check", ctx ->
                ctx.json(quickCheck.execute(ctx.pathParam("id"))));
    }
}
