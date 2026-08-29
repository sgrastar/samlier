package org.samlier.peer.sp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.RunStatus;
import org.samlier.runner.RunEventBus;
import org.samlier.runner.RunService;
import org.samlier.saml.crypto.FilePlanKeyStore;
import org.samlier.saml.crypto.XmlSigner;
import org.samlier.saml.metadata.MetadataService;
import org.samlier.saml.metadata.TargetMetadataParser;
import org.samlier.saml.normal.OpenSamlReader;
import org.samlier.saml.normal.SamlProtocolService;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.raw.XmlDoctypeDetector;
import org.samlier.store.FileTranscriptRecorder;
import org.samlier.store.JsonCodec;
import org.samlier.store.MetadataCache;
import org.samlier.store.SqliteDatabase;
import org.samlier.store.SqlitePlanRepository;
import org.samlier.store.SqliteRunRepository;

class SpPeerRoundTripTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void completesOneNormalBrowserRoundTripWithoutProducingAVerdict() {
        var now = Instant.parse("2026-08-29T00:00:00Z");
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        var plans = new SqlitePlanRepository(database, json);
        var runs = new SqliteRunRepository(database, json);
        var cache = new MetadataCache(directory);
        var plan = plan("plan_0123456789ABCDEFGHJKMNPQRS", PlanProfile.IDP_CORE, TargetKind.IDP,
                "https://idp.example/entity", now);
        plans.save(plan);
        cache.put(plan.id(), idpMetadata());
        var runService = new RunService(plans, runs, new RunEventBus(), clock);
        var run = runService.create(plan.id());
        var signer = new XmlSigner();
        var saml = new SamlProtocolService(URI.create("https://peer.example"),
                new FilePlanKeyStore(directory, clock), signer, new OpenSamlReader(), clock);
        var recorder = new FileTranscriptRecorder(database, json, directory);
        var peer = new SpPeerService(plans, runs, runService, cache, new TargetMetadataParser(),
                saml, recorder, clock);

        var redirect = peer.start(plan.id(), run.id());
        var request = saml.decodeRedirect(redirect.getRawQuery(), "SAMLRequest");
        var responsePlan = plan("plan_1123456789ABCDEFGHJKMNPQRS", PlanProfile.SP_CORE, TargetKind.SP,
                "https://peer.example/p/" + plan.id(), now);
        var response = saml.buildResponse(responsePlan, request,
                URI.create("https://peer.example/p/" + plan.id() + "/sp/acs/0"), "smoke-user");
        var body = "SAMLResponse=" + URLEncoder.encode(response.base64(), StandardCharsets.UTF_8)
                + "&RelayState=" + URLEncoder.encode(run.id(), StandardCharsets.UTF_8);

        peer.consume(plan.id(), body.getBytes(StandardCharsets.UTF_8), Map.of(),
                "https://peer.example/p/" + plan.id() + "/sp/acs/0");

        var completed = runs.find(run.id()).orElseThrow();
        assertEquals(RunStatus.COMPLETED, completed.status());
        assertEquals("completed", completed.context().get("m0RoundTrip"));
        assertEquals(2, recorder.list(run.id()).size());
        assertTrue(completed.context().keySet().stream().noneMatch(key -> key.toLowerCase().contains("verdict")));

        var probeBody = "SAMLResponse=" + URLEncoder.encode(response.base64(), StandardCharsets.UTF_8);
        peer.consume(plan.id(), probeBody.getBytes(StandardCharsets.UTF_8), Map.of(),
                "https://peer.example/p/" + plan.id() + "/sp/acs/0?mdv=no-key-info&run=" + run.id());
        assertEquals(3, recorder.list(run.id()).size());
        assertEquals(RunStatus.COMPLETED, runs.find(run.id()).orElseThrow().status());
        assertEquals("completed", runs.find(run.id()).orElseThrow().context().get("m0RoundTrip"),
                "probe traffic must not rewrite the normal round-trip state");
    }

    @Test
    void recordsTargetResponseBeforeDtdRejection() {
        var now = Instant.parse("2026-08-29T00:00:00Z");
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        var plans = new SqlitePlanRepository(database, json);
        var runs = new SqliteRunRepository(database, json);
        var plan = plan("plan_0123456789ABCDEFGHJKMNPQRS", PlanProfile.IDP_CORE, TargetKind.IDP,
                "https://idp.example/entity", now);
        plans.save(plan);
        var runService = new RunService(plans, runs, new RunEventBus(), clock);
        var run = runService.create(plan.id());
        var saml = new SamlProtocolService(URI.create("https://peer.example"),
                new FilePlanKeyStore(directory, clock), new XmlSigner(), new OpenSamlReader(), clock);
        var recorder = new FileTranscriptRecorder(database, json, directory);
        var peer = new SpPeerService(
                plans, runs, runService, new MetadataCache(directory), new TargetMetadataParser(),
                saml, recorder, clock);
        var xml = "<!DOCTYPE Response [<!ELEMENT Response EMPTY>]><Response/>"
                .getBytes(StandardCharsets.UTF_8);
        var body = "SAMLResponse=" + URLEncoder.encode(
                Base64.getEncoder().encodeToString(xml), StandardCharsets.UTF_8)
                + "&RelayState=" + URLEncoder.encode(run.id(), StandardCharsets.UTF_8);

        assertThrows(SamlException.class, () -> peer.consume(
                plan.id(), body.getBytes(StandardCharsets.UTF_8), Map.of(), "https://peer.example/acs"));

        var entry = recorder.list(run.id()).getFirst();
        assertTrue(XmlDoctypeDetector.containsDoctype(recorder.readDecodedSaml(entry)));
        assertEquals("not-yet-parsed", entry.samlSummary().get("parseStatus"));
    }

    private TestPlan plan(String id, PlanProfile profile, TargetKind kind, String entityId, Instant now) {
        return new TestPlan(id, "Round trip", profile,
                new TestPlan.Target(kind, entityId,
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://target.example/metadata")),
                MetadataDeliveryKind.HTTP_URL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), now, now);
    }

    private byte[] idpMetadata() {
        return """
                <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata"
                    entityID="https://idp.example/entity">
                  <md:IDPSSODescriptor protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                    <md:SingleSignOnService
                        Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"
                        Location="https://idp.example/sso"/>
                  </md:IDPSSODescriptor>
                </md:EntityDescriptor>
                """.getBytes(StandardCharsets.UTF_8);
    }
}
