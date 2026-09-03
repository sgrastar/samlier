package com.samlscope.api;

import io.javalin.config.JavalinConfig;
import java.net.URI;
import java.util.Objects;

final class PublicationRoutes {
    private PublicationRoutes() {}

    @FunctionalInterface
    interface Publisher { Published publish(String runId); }

    static void register(JavalinConfig javalin, Publisher publisher) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(publisher, "publisher");
        javalin.routes.post("/api/runs/{id}/publish", ctx -> ctx.json(publisher.publish(ctx.pathParam("id"))));
    }

    record Published(String runId, URI publicUrl) {}
}
