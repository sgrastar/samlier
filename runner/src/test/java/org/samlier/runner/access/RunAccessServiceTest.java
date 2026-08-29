package org.samlier.runner.access;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.run.RunStatus;
import org.samlier.core.run.TestRun;
import org.samlier.store.JsonCodec;
import org.samlier.store.SqliteDatabase;
import org.samlier.store.SqlitePlanRepository;
import org.samlier.store.SqliteRunAccessGrantRepository;
import org.samlier.store.SqliteRunRepository;

class RunAccessServiceTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void rawTokensNeverPersistAndRotationAndCsrfFailClosed() throws Exception {
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        var plans = new SqlitePlanRepository(database, json);
        var runs = new SqliteRunRepository(database, json);
        var plan = plan();
        var run = new TestRun("run_0123456789ABCDEFGHJKMNPQRS", plan.id(), RunStatus.CREATED,
                Reachability.UNKNOWN, Map.of(), Instant.EPOCH, Instant.EPOCH);
        plans.save(plan);
        runs.save(run);
        var service = new RunAccessService(URI.create("https://app.example"), runs,
                new SqliteRunAccessGrantRepository(database), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        var issued = service.issue(run.id());
        var accessToken = issued.managementUrl().getFragment().substring(2);
        assertTrue(issued.managementUrl().toString().contains("#t="));
        assertFalse(new String(Files.readAllBytes(directory.resolve("samlier.db")), StandardCharsets.ISO_8859_1)
                .contains(accessToken));

        var first = service.exchange(run.id(), accessToken);
        assertTrue(service.authorizeSession(first.sessionToken()).equals(run.id()));
        service.authorize(run.id(), first.sessionToken());
        service.authorizeMutation(run.id(), first.sessionToken(), first.csrfToken());
        assertThrows(SecurityException.class, () ->
                service.authorizeMutation(run.id(), first.sessionToken(), "x".repeat(43)));

        var second = service.exchange(run.id(), accessToken);
        assertNotEquals(first.sessionToken(), second.sessionToken());
        assertThrows(SecurityException.class, () -> service.authorizeSession(first.sessionToken()));
        assertThrows(SecurityException.class, () -> service.authorize(run.id(), first.sessionToken()));
        service.revoke(run.id());
        assertThrows(SecurityException.class, () -> service.exchange(run.id(), accessToken));
        assertThrows(SecurityException.class, () -> service.authorize(run.id(), second.sessionToken()));
    }

    private TestPlan plan() {
        return new TestPlan("plan_0123456789ABCDEFGHJKMNPQRS", "Access", PlanProfile.IDP_CORE,
                new TestPlan.Target(TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), Instant.EPOCH, Instant.EPOCH);
    }
}
