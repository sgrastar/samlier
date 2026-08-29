package org.samlier.api;

import io.javalin.config.JavalinConfig;
import java.util.Objects;
import org.samlier.runner.InteractionQuery;

/** Read-only projection of pending user interactions. */
public final class InteractionRoutes {
    private InteractionRoutes() {}

    public static void register(JavalinConfig javalin, InteractionQuery interactions) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(interactions, "interactions");
        javalin.routes.get("/api/runs/{id}/interactions", ctx ->
                ctx.json(interactions.pending(ctx.pathParam("id"))));
    }
}
