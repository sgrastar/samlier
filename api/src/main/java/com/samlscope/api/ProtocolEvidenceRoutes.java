package com.samlscope.api;

import io.javalin.config.JavalinConfig;
import java.util.Objects;
import java.util.function.Function;
import com.samlscope.runner.ProtocolEvidenceAutomationService;

/** Readiness and evaluation endpoints for cases backed by recorded protocol evidence. */
public final class ProtocolEvidenceRoutes {
    private ProtocolEvidenceRoutes() {}

    public static void register(
            JavalinConfig javalin,
            Function<String, ProtocolEvidenceAutomationService.Status> status,
            Function<String, ProtocolEvidenceAutomationService.Evaluation> evaluate,
            Function<String, ProtocolEvidenceAutomationService.Evaluation> confirmAttempts) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(evaluate, "evaluate");
        Objects.requireNonNull(confirmAttempts, "confirmAttempts");
        javalin.routes.get("/api/runs/{id}/protocol-evidence", ctx ->
                ctx.json(status.apply(ctx.pathParam("id"))));
        javalin.routes.post("/api/runs/{id}/protocol-evidence/evaluate", ctx ->
                ctx.json(evaluate.apply(ctx.pathParam("id"))));
        javalin.routes.post("/api/runs/{id}/protocol-evidence/confirm-attempts", ctx ->
                ctx.json(confirmAttempts.apply(ctx.pathParam("id"))));
    }
}
