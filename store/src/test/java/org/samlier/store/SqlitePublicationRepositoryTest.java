package org.samlier.store;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
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

class SqlitePublicationRepositoryTest {
    private static final String RUN = "run_0123456789ABCDEFGHJKMNPQRS";
    @TempDir java.nio.file.Path directory;

    @Test
    void publicationIsExplicitAndIdempotent() {
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        var now = Instant.parse("2026-08-29T00:00:00Z");
        var plan = new TestPlan(
                "plan_0123456789ABCDEFGHJKMNPQRS", "Publication", PlanProfile.IDP_CORE,
                new TestPlan.Target(TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), now, now);
        new SqlitePlanRepository(database, json).save(plan);
        new SqliteRunRepository(database, json).save(new TestRun(
                RUN, plan.id(), RunStatus.COMPLETED, Reachability.CONFIRMED, Map.of(), now, now));
        var repository = new SqlitePublicationRepository(database);

        assertFalse(repository.isPublished(RUN));
        repository.publish(RUN, now);
        repository.publish(RUN, now.plusSeconds(1));
        assertTrue(repository.isPublished(RUN));
    }
}
