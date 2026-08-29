package org.samlier.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
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
import org.samlier.core.transcript.Direction;
import org.samlier.core.transcript.TranscriptInput;

class FileTranscriptRecorderTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void credentialsNeverReachPersistentStorage() throws Exception {
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        var now = Instant.parse("2026-08-29T00:00:00Z");
        var plan = new TestPlan("plan_0123456789ABCDEFGHJKMNPQRS", "Example", PlanProfile.IDP_CORE,
                new TestPlan.Target(TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), now, now);
        new SqlitePlanRepository(database, json).save(plan);
        var run = new TestRun("run_0123456789ABCDEFGHJKMNPQRS", plan.id(), RunStatus.RUNNING,
                Reachability.UNKNOWN, Map.of(), now, now);
        new SqliteRunRepository(database, json).save(run);
        var recorder = new FileTranscriptRecorder(database, json, directory);
        var secret = "correct horse battery staple";
        recorder.record(new TranscriptInput(run.id(), Direction.INBOUND, now, "corr", "POST",
                "https://suite.example/ecp?token=" + secret, 200,
                Map.of("Authorization", List.of("Basic " + secret), "Cookie", List.of("sid=" + secret)),
                ("username=test&password=" + secret).getBytes(StandardCharsets.UTF_8),
                "application/x-www-form-urlencoded", "token=" + secret, new byte[0], Map.of()));

        var allBytes = new String(readAll(directory), StandardCharsets.ISO_8859_1);
        assertFalse(allBytes.contains(secret));
        assertTrue(allBytes.contains("redacted"));
    }

    @Test
    void readsOnlyTheDecodedSamlBoundToATranscriptEntry() {
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        var now = Instant.parse("2026-08-29T00:00:00Z");
        var plan = new TestPlan("plan_0123456789ABCDEFGHJKMNPQRS", "Example", PlanProfile.IDP_CORE,
                new TestPlan.Target(TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), now, now);
        new SqlitePlanRepository(database, json).save(plan);
        var run = new TestRun("run_0123456789ABCDEFGHJKMNPQRS", plan.id(), RunStatus.RUNNING,
                Reachability.UNKNOWN, Map.of(), now, now);
        new SqliteRunRepository(database, json).save(run);
        var recorder = new FileTranscriptRecorder(database, json, directory);
        var xml = "<samlp:Response xmlns:samlp='urn:oasis:names:tc:SAML:2.0:protocol'/>"
                .getBytes(StandardCharsets.UTF_8);
        var entry = recorder.record(new TranscriptInput(
                run.id(), Direction.INBOUND, now, "corr", "POST", "https://suite.example/acs", 200,
                Map.of(), new byte[0], "application/xml", null, xml, Map.of()));

        assertArrayEquals(xml, recorder.readDecodedSaml(entry));
    }

    private byte[] readAll(java.nio.file.Path root) throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        try (var paths = Files.walk(root)) {
            for (var path : paths.filter(Files::isRegularFile).toList()) {
                output.write(Files.readAllBytes(path));
            }
        }
        return output.toByteArray();
    }
}
