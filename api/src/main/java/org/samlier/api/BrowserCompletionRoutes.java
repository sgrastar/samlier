package org.samlier.api;

import io.javalin.config.JavalinConfig;
import java.util.Objects;
import org.samlier.runner.BrowserCompletionExecutor;

/** Advances a browser wait; the endpoint accepts no outcome or verdict. */
public final class BrowserCompletionRoutes {
    private BrowserCompletionRoutes() {}

    public static void register(JavalinConfig javalin, BrowserCompletionExecutor browser) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(browser, "browser");
        javalin.routes.post("/api/runs/{id}/cases/{caseId}/browser-complete", ctx -> {
            if (!ctx.body().isBlank() && !"{}".equals(ctx.body().trim())) {
                throw new IllegalArgumentException("Browser completion does not accept an outcome");
            }
            ctx.json(browser.complete(ctx.pathParam("id"), ctx.pathParam("caseId")));
        });
    }
}
