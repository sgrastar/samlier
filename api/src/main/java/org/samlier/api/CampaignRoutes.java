package org.samlier.api;

import io.javalin.config.JavalinConfig;
import java.util.Objects;
import org.samlier.runner.RunCampaignQuery;

/** Read-only Quick / Standard / Full evidence-campaign projection. */
public final class CampaignRoutes {
    private CampaignRoutes() {}

    public static void register(JavalinConfig javalin, RunCampaignQuery campaigns) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(campaigns, "campaigns");
        javalin.routes.get("/api/runs/{id}/campaigns", ctx ->
                ctx.json(campaigns.report(ctx.pathParam("id"))));
    }
}
