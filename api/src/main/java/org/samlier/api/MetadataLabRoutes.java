package org.samlier.api;

import io.javalin.config.JavalinConfig;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.samlier.runner.MetadataLabService;

/** Management API for the Suite-controlled side of a standard SAML metadata feed. */
public final class MetadataLabRoutes {
    private MetadataLabRoutes() {}

    public static void register(JavalinConfig javalin, MetadataLabService lab) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(lab, "lab");
        javalin.routes.get("/api/runs/{id}/metadata-lab", ctx ->
                ctx.json(lab.state(ctx.pathParam("id"))));
        javalin.routes.post("/api/runs/{id}/metadata-lab/variant", ctx -> {
            var body = ctx.bodyAsClass(Map.class);
            if (body == null || !Set.of("variant").equals(body.keySet())
                    || !(body.get("variant") instanceof String variant) || variant.isBlank()) {
                throw new IllegalArgumentException("Metadata lab body must contain only a non-empty variant");
            }
            ctx.json(lab.select(ctx.pathParam("id"), variant));
        });
    }
}
