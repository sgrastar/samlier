package com.samlscope.api;

import io.javalin.config.JavalinConfig;
import java.util.Objects;
import com.samlscope.runner.CampaignActionCompletionService;

/** Completes a server-defined shared operator action; request bodies cannot supply outcomes. */
public final class CampaignActionRoutes {
    private CampaignActionRoutes() {}

    public static void register(
            JavalinConfig javalin,
            CampaignActionCompletionService actions) {
        registerBounded(javalin, actions::complete);
    }

    static void registerBounded(
            JavalinConfig javalin,
            Completion actions) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(actions, "actions");
        javalin.routes.post("/api/runs/{id}/campaigns/{campaignId}/actions/{actionId}/complete", ctx -> {
            if (!ctx.body().isBlank() && !"{}".equals(ctx.body().trim())) {
                throw new IllegalArgumentException("Campaign action completion does not accept an outcome");
            }
            ctx.json(actions.complete(
                    ctx.pathParam("id"), ctx.pathParam("campaignId"), ctx.pathParam("actionId")));
        });
    }

    @FunctionalInterface
    public interface Completion {
        CampaignActionCompletionService.Result complete(
                String runId, String campaignId, String actionId);
    }
}
