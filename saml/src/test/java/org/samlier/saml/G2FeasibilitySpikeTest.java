package org.samlier.saml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.samlier.saml.binding.RedirectSignatureInput;
import org.samlier.saml.ecp.EcpEnvelopeForwarder;
import org.samlier.saml.logout.LogoutExchange;
import org.samlier.saml.metadata.MetadataVariantRegistry;
import org.samlier.saml.normal.SecureXml;
import org.samlier.saml.raw.BytePreservingRawMessageBuilder;
import org.samlier.saml.raw.RawMessageBuilder;

/** Architectural feasibility probes only. These are not verdict cases. */
final class G2FeasibilitySpikeTest {
    private static final String SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String CB = "urn:oasis:names:tc:SAML:protocol:ext:channel-binding";
    private static final String SAML_EC = "urn:ietf:params:xml:ns:samlec";

    @Test
    void s1ForwarderRemovesEveryServiceProviderHeader() {
        var forwarded = new EcpEnvelopeForwarder().removeServiceProviderHeaders(ecpEnvelope("same", "same", true));
        var document = SecureXml.parse(forwarded);
        var header = document.getElementsByTagNameNS(SOAP, "Header").item(0);
        assertEquals(0, header.getChildNodes().getLength());
    }

    @Test
    void s1ForwarderPreservesGeneratedKeyAdvice() {
        var forwarded = new EcpEnvelopeForwarder().removeServiceProviderHeaders(ecpEnvelope("same", "same", true));
        var document = SecureXml.parse(forwarded);
        assertEquals(1, document.getElementsByTagNameNS(SAML_EC, "GeneratedKey").getLength());
        assertEquals(1, document.getElementsByTagNameNS(CB, "ChannelBindings").getLength());
    }

    @Test
    void s1ChannelBindingVariantsRemainDistinguishable() throws Exception {
        assertNotEquals(bindingVector(ecpEnvelope("request", "header", true)),
                bindingVector(ecpEnvelope("same", "same", true)));
        assertNotEquals(bindingVector(ecpEnvelope("request", null, true)),
                bindingVector(ecpEnvelope(null, "header", true)));
        assertNotEquals(bindingVector(ecpEnvelope("same", "same", true)),
                bindingVector(ecpEnvelope("same", "same", false)));
    }

    @Test
    void s2FrontChannelAndSoapShareSemanticModelWithoutLosingTransport() {
        var xml = logoutRequest(false);
        var front = LogoutExchange.parse(xml, LogoutExchange.Transport.FRONT_CHANNEL);
        var soap = LogoutExchange.parse(xml, LogoutExchange.Transport.SOAP);
        assertEquals(front.messageType(), soap.messageType());
        assertNotEquals(front.transport(), soap.transport());
    }

    @Test
    void s2AsyncExtensionIsIndependentlyObservable() {
        assertFalse(LogoutExchange.parse(logoutRequest(false), LogoutExchange.Transport.SOAP).asynchronous());
        assertTrue(LogoutExchange.parse(logoutRequest(true), LogoutExchange.Transport.SOAP).asynchronous());
    }

    @Test
    void s2SessionDestructionRequiresResponseObservation() {
        assertTrue(LogoutExchange.isSafeDestructiveOrder(List.of(
                LogoutExchange.Event.SESSION_ESTABLISHED, LogoutExchange.Event.LOGOUT_SENT,
                LogoutExchange.Event.RESPONSE_OBSERVED, LogoutExchange.Event.SESSION_DESTROYED)));
        assertFalse(LogoutExchange.isSafeDestructiveOrder(List.of(
                LogoutExchange.Event.SESSION_ESTABLISHED, LogoutExchange.Event.SESSION_DESTROYED,
                LogoutExchange.Event.LOGOUT_SENT, LogoutExchange.Event.RESPONSE_OBSERVED)));
    }

    @Test
    void s3OriginalVariantSelectorSurvivesAllRequiredRedirects() throws Exception {
        var status = new AtomicInteger(301);
        var server = metadataServer(status, new MetadataVariantRegistry());
        server.start();
        try {
            var source = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/source/entity?variant=expired-valid-until");
            var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
            for (var code : List.of(301, 302, 307)) {
                status.set(code);
                var response = client.send(HttpRequest.newBuilder(source).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals("query:variant=expired-valid-until", response.body());
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void s3StableMetadataUrlExposesSelectedDeterministicVariant() throws Exception {
        var registry = new MetadataVariantRegistry();
        var server = metadataServer(new AtomicInteger(302), registry);
        server.start();
        try {
            var url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mdq/entity");
            var client = HttpClient.newHttpClient();
            registry.select("entity", "not-yet-valid");
            var first = client.send(HttpRequest.newBuilder(url).build(), HttpResponse.BodyHandlers.ofString()).body();
            var repeated = client.send(HttpRequest.newBuilder(url).build(), HttpResponse.BodyHandlers.ofString()).body();
            assertEquals(first, repeated);
            registry.select("entity", "expired-valid-until");
            var changed = client.send(HttpRequest.newBuilder(url).build(), HttpResponse.BodyHandlers.ofString()).body();
            assertNotEquals(first, changed);
            assertEquals(url, URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mdq/entity"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void s5ProductionRawBuilderPreservesExactBytes() {
        var raw = "<!DOCTYPE x [<!ENTITY e 'ok'>]><x unknown=\"yes\" value=\"&#x9;\">&e;</x>"
                .getBytes(StandardCharsets.UTF_8);
        var built = new BytePreservingRawMessageBuilder().build(new RawMessageBuilder.RawMessageSpec("g2-raw", raw));
        assertArrayEquals(raw, built);
        raw[0] = 0;
        assertNotEquals(raw[0], built[0]);
    }

    @Test
    void s5BoundaryLengthsUseCodePointsWithoutIsolatedSurrogates() {
        var value = "x".repeat(255) + "\uD83D\uDE00";
        var builder = new BytePreservingRawMessageBuilder();
        assertEquals(256, value.codePointCount(0, value.length()));
        assertEquals(value, new String(builder.encodeValue(value, 256), StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> builder.encodeValue(value + "x", 256));
        assertThrows(IllegalArgumentException.class, () -> builder.encodeValue("\uD800", 256));
    }

    @Test
    void s6SignatureInputPreservesRawEncodedOctetsAndCanonicalParameterOrder() {
        var raw = "SigAlg=http%3A%2F%2Falg&RelayState=x%20y%2Fz&SAMLRequest=a%2Fb%2Bc&Signature=sig";
        var signed = new String(RedirectSignatureInput.fromRawQuery(raw), StandardCharsets.US_ASCII);
        assertEquals("SAMLRequest=a%2Fb%2Bc&RelayState=x%20y%2Fz&SigAlg=http%3A%2F%2Falg", signed);
        assertFalse(signed.contains("a/b+c"));
    }

    @Test
    void s6AmbiguousOrMalformedSignedParametersAreRejected() {
        assertThrows(RuntimeException.class, () -> RedirectSignatureInput.fromRawQuery("SAMLRequest=a&SAMLRequest=b&SigAlg=alg"));
        assertThrows(RuntimeException.class, () -> RedirectSignatureInput.fromRawQuery("SAMLRequest=a&SigAlg=x&SigAlg=y"));
        assertThrows(RuntimeException.class, () -> RedirectSignatureInput.fromRawQuery("SAMLRequest=a&RelayState=x&RelayState=y&SigAlg=alg"));
        assertThrows(RuntimeException.class, () -> RedirectSignatureInput.fromRawQuery("SAMLRequest=a&SigAlg=alg&SAMLResponse=b"));
        assertThrows(RuntimeException.class, () -> RedirectSignatureInput.fromRawQuery("SAMLRequest=a&SigAlg"));
        assertThrows(RuntimeException.class, () -> RedirectSignatureInput.fromRawQuery("SAMLRequest=é&SigAlg=alg"));
        assertEquals("SAMLResponse=r&SigAlg=alg", new String(
                RedirectSignatureInput.fromRawQuery("SigAlg=alg&SAMLResponse=r"), StandardCharsets.US_ASCII));
    }

    private static byte[] ecpEnvelope(String adviceBinding, String headerBinding, boolean signed) {
        var header = headerBinding == null ? "" : "<cb:ChannelBindings Type=\"tls-unique\">" + headerBinding + "</cb:ChannelBindings>";
        var advice = adviceBinding == null ? "" : "<cb:ChannelBindings Type=\"tls-unique\">" + adviceBinding + "</cb:ChannelBindings>";
        var signature = signed ? "<ds:Signature><ds:SignedInfo/></ds:Signature>" : "";
        return ("<soap:Envelope xmlns:soap=\"" + SOAP + "\" xmlns:cb=\"" + CB
                + "\" xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" xmlns:samlec=\"" + SAML_EC
                + "\" xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\"><soap:Header>" + header
                + "</soap:Header><soap:Body><saml:Assertion>" + signature + "<saml:Advice>" + advice
                + "<samlec:GeneratedKey>AAECAwQFBgcICQ==</samlec:GeneratedKey></saml:Advice>"
                + "</saml:Assertion></soap:Body></soap:Envelope>").getBytes(StandardCharsets.UTF_8);
    }

    private static String bindingVector(byte[] xml) throws Exception {
        var document = SecureXml.parse(xml);
        var xpath = XPathFactory.newInstance().newXPath();
        return xpath.evaluate("string(/*[local-name()='Envelope']/*[local-name()='Header']/*[local-name()='ChannelBindings'])", document)
                + '|' + xpath.evaluate("string(//*[local-name()='Advice']/*[local-name()='ChannelBindings'])", document)
                + '|' + document.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature").getLength();
    }

    private static byte[] logoutRequest(boolean asynchronous) {
        var extension = asynchronous ? "<samlp:Extensions><aslo:Asynchronous/></samlp:Extensions>" : "";
        return ("<samlp:LogoutRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
                + "xmlns:aslo=\"urn:oasis:names:tc:SAML:2.0:protocol:ext:async-slo\" ID=\"_logout\" "
                + "Version=\"2.0\" IssueInstant=\"2026-08-29T00:00:00Z\">" + extension
                + "</samlp:LogoutRequest>").getBytes(StandardCharsets.UTF_8);
    }

    private static HttpServer metadataServer(AtomicInteger redirectStatus, MetadataVariantRegistry registry) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/source/entity", exchange -> {
            var query = exchange.getRequestURI().getRawQuery();
            exchange.getResponseHeaders().add("Location", "/mdq/entity" + (query == null ? "" : "?" + query));
            exchange.sendResponseHeaders(redirectStatus.get(), -1);
            exchange.close();
        });
        server.createContext("/mdq/entity", exchange -> {
            var query = exchange.getRequestURI().getRawQuery();
            var body = (query == null ? "selected:" + registry.selected("entity") : "query:" + query)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        return server;
    }
}
