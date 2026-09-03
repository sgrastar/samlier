package com.samlscope.api;

import io.javalin.config.JavalinConfig;
import java.util.Objects;
import com.samlscope.runner.QuickCheckExecutor;

/** Starts the approved M1 execution slice after the baseline SSO round trip. */
public final class QuickCheckRoutes {
    private QuickCheckRoutes() {}

    public static void register(JavalinConfig javalin, QuickCheckExecutor quickCheck) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(quickCheck, "quickCheck");
        javalin.routes.post("/api/runs/{id}/quick-check", ctx ->
                ctx.json(quickCheck.execute(ctx.pathParam("id"))));
    }
}
