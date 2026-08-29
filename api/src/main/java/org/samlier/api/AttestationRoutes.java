package org.samlier.api;

import io.javalin.config.JavalinConfig;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.samlier.runner.AttestationExecutor;

/** Isolated attestation route; server-side case definitions own every option-to-outcome mapping. */
public final class AttestationRoutes {
    private AttestationRoutes() {}

    public static void register(JavalinConfig javalin, AttestationExecutor attestations) {
        Objects.requireNonNull(javalin, "javalin");
        Objects.requireNonNull(attestations, "attestations");
        javalin.routes.post("/api/runs/{id}/cases/{caseId}/attest", ctx -> {
            var request = request(ctx.bodyAsClass(Map.class));
            ctx.json(attestations.attest(
                    ctx.pathParam("id"), ctx.pathParam("caseId"), request.value(), request.note()));
        });
    }

    private static AttestationRequest request(Map<?, ?> body) {
        if (body == null || !Set.of("value", "note").containsAll(body.keySet())) {
            throw new IllegalArgumentException("Attestation body contains an unknown field");
        }
        var value = body.get("value");
        var note = body.get("note");
        if (!(value instanceof String option) || option.isBlank()) {
            throw new IllegalArgumentException("Attestation value must not be blank");
        }
        if (note != null && !(note instanceof String)) {
            throw new IllegalArgumentException("Attestation note must be a string");
        }
        return new AttestationRequest(option, note == null ? "" : (String) note);
    }

    private record AttestationRequest(String value, String note) {}
}
