package org.samlier.peer.logout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
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
import org.samlier.saml.normal.SecureXml;
import org.samlier.store.FileTranscriptRecorder;
import org.samlier.store.JsonCodec;
import org.samlier.store.MetadataCache;
import org.samlier.store.SqliteDatabase;
import org.samlier.store.SqlitePlanRepository;
import org.samlier.store.SqliteRunRepository;

class SloPeerServiceTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void recordsPostRequestAndBuildsACorrelatedSignedLogoutResponse() {
        var fixture = fixture();
        var body = "SAMLRequest=" + URLEncoder.encode(
                Base64.getEncoder().encodeToString(logoutRequest()), StandardCharsets.UTF_8);
        var result = fixture.service.consume(
                fixture.plan.id(), SloPeerService.Transport.FRONT_CHANNEL, "POST", null,
                body.getBytes(StandardCharsets.UTF_8), Map.of(),
                "https://suite.example/p/" + fixture.plan.id() + "/sp/slo?run=" + fixture.runId);

        assertEquals("LogoutRequest", result.messageType());
        assertEquals(MetadataService.POST, result.responseBinding());
        var response = SecureXml.parse(result.response().xml()).getDocumentElement();
        assertEquals("LogoutResponse", response.getLocalName());
        assertEquals("_logout", response.getAttribute("InResponseTo"));
        assertEquals(2, fixture.recorder.list(fixture.runId).size());
    }

    @Test
    void preservesSoapTransportAndReturnsASoapEnvelope() {
        var fixture = fixture();
        var request = """
                <S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/">
                  <S:Body>%s</S:Body>
                </S:Envelope>
                """.formatted(new String(logoutRequest(), StandardCharsets.UTF_8));
        var result = fixture.service.consume(
                fixture.plan.id(), SloPeerService.Transport.SOAP, "POST", null,
                request.getBytes(StandardCharsets.UTF_8), Map.of(),
                "https://suite.example/p/" + fixture.plan.id() + "/sp/slo/soap?run=" + fixture.runId);

        assertEquals(MetadataService.SOAP, result.responseBinding());
        var envelope = SecureXml.parse(fixture.service.soapResponse(result));
        assertEquals("Envelope", envelope.getDocumentElement().getLocalName());
        assertTrue(envelope.getElementsByTagNameNS(
                "urn:oasis:names:tc:SAML:2.0:protocol", "LogoutResponse").getLength() == 1);
    }

    private Fixture fixture() {
        var now = Instant.parse("2026-08-29T00:00:00Z");
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        var plans = new SqlitePlanRepository(database, json);
        var runs = new SqliteRunRepository(database, json);
        var plan = new TestPlan(
                "plan_0123456789ABCDEFGHJKMNPQRS", "IdP target", PlanProfile.IDP_FULL,
                new TestPlan.Target(TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.HTTP_URL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), now, now);
        plans.save(plan);
        var runService = new RunService(plans, runs, new RunEventBus(), clock);
        var run = runService.create(plan.id());
        runService.update(run, RunStatus.COMPLETED, run.targetToSuiteReachability(), Map.of());
        var cache = new MetadataCache(directory);
        cache.put(plan.id(), targetMetadata());
        var recorder = new FileTranscriptRecorder(database, json, directory);
        var service = new SloPeerService(
                plans, runs, cache, new TargetMetadataParser(),
                new SamlProtocolService(URI.create("https://suite.example"),
                        new FilePlanKeyStore(directory, clock), new XmlSigner(), new OpenSamlReader(), clock),
                recorder, clock);
        return new Fixture(plan, run.id(), service, recorder);
    }

    private byte[] targetMetadata() {
        return """
                <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata"
                    entityID="https://idp.example/entity">
                  <md:IDPSSODescriptor protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                    <md:SingleLogoutService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
                        Location="https://idp.example/slo/post"/>
                    <md:SingleLogoutService Binding="urn:oasis:names:tc:SAML:2.0:bindings:SOAP"
                        Location="https://idp.example/slo/soap"/>
                  </md:IDPSSODescriptor>
                </md:EntityDescriptor>
                """.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] logoutRequest() {
        return """
                <samlp:LogoutRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                    xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                    ID="_logout" Version="2.0" IssueInstant="2026-08-29T00:00:00Z">
                  <saml:Issuer>https://idp.example/entity</saml:Issuer>
                  <saml:NameID>user</saml:NameID>
                </samlp:LogoutRequest>
                """.getBytes(StandardCharsets.UTF_8);
    }

    private record Fixture(
            TestPlan plan, String runId, SloPeerService service, FileTranscriptRecorder recorder) {}
}
