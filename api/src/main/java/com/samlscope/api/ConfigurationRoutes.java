package com.samlscope.api;

import io.javalin.config.JavalinConfig;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.samlscope.runner.ConfigurationExecutor;

/** Isolated route for the fixed common configuration-answer vocabulary. */
public final class ConfigurationRoutes {
    private ConfigurationRoutes() {}

    public static void register(JavalinConfig javalin, ConfigurationExecutor configurations) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(configurations, "configurations");
        javalin.routes.post("/api/runs/{id}/cases/{caseId}/configure", ctx -> {
            var request = request(ctx.bodyAsClass(Map.class));
            ctx.json(configurations.answer(
                    ctx.pathParam("id"), ctx.pathParam("caseId"), request.value(), request.note()));
        });
    }

    private static ConfigurationRequest request(Map<?, ?> body) {
        if (body == null || !Set.of("value", "note").containsAll(body.keySet())) {
            throw new IllegalArgumentException("Configuration body contains an unknown field");
        }
        var value = body.get("value");
        var note = body.get("note");
        if (!(value instanceof String answer) || answer.isBlank()) {
            throw new IllegalArgumentException("Configuration value must not be blank");
        }
        if (note != null && !(note instanceof String)) {
            throw new IllegalArgumentException("Configuration note must be a string");
        }
        return new ConfigurationRequest(answer, note == null ? "" : (String) note);
    }

    private record ConfigurationRequest(String value, String note) {}
}
