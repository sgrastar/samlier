package org.samlier.api;

import io.javalin.config.JavalinConfig;
import java.util.List;
import java.util.Objects;
import org.samlier.core.caseexec.CaseExecution;

/** Starts an approved milestone's applicable interactive execution slices. */
public final class MilestoneRoutes {
    private MilestoneRoutes() {}

    @FunctionalInterface
    public interface Starter { List<CaseExecution> start(String runId, String milestone); }

    public static void register(JavalinConfig javalin, Starter starter) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(starter, "starter");
        javalin.routes.post("/api/runs/{id}/milestones/{milestone}/start", ctx ->
                ctx.json(starter.start(ctx.pathParam("id"), ctx.pathParam("milestone"))));
    }
}
