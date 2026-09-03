package com.samlscope.api;

import io.javalin.config.JavalinConfig;
import java.util.Objects;
import com.samlscope.runner.BootstrapContractQuery;

/** Exposes shared setup contracts without accepting case verdicts or vendor-specific credentials. */
public final class BootstrapContractRoutes {
    private BootstrapContractRoutes() {}

    public static void register(JavalinConfig javalin, BootstrapContractQuery contracts) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(contracts, "contracts");
        javalin.routes.get("/api/runs/{id}/bootstrap-contracts", ctx ->
                ctx.json(contracts.contracts(ctx.pathParam("id"))));
    }
}
