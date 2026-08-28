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
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.samlier.saml.binding.RedirectSignatureInput;
import org.samlier.saml.normal.SecureXml;
import org.samlier.saml.raw.RawMessageBuilder;
import org.w3c.dom.Element;

/** Architectural feasibility probes only. These are not verdict cases. */
final class G2FeasibilitySpikeTest {
    @Test
    void s1EcpAndSamlEcCanPreserveRequiredChannelBindingLocations() throws Exception {
        var xml = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:cb="urn:oasis:names:tc:SAML:protocol:ext:channel-binding"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  xmlns:samlec="urn:ietf:params:xml:ns:samlec">
                  <soap:Header><cb:ChannelBindings Type="tls-unique">client-sp</cb:ChannelBindings></soap:Header>
                  <soap:Body><saml:Assertion><saml:Advice>
                    <cb:ChannelBindings Type="tls-unique">client-sp</cb:ChannelBindings>
                    <samlec:GeneratedKey>AAECAwQFBgcICQ==</samlec:GeneratedKey>
                  </saml:Advice></saml:Assertion></soap:Body>
                </soap:Envelope>
                """.getBytes(StandardCharsets.UTF_8);
        var document = SecureXml.parse(xml);
        assertEquals(2, document.getElementsByTagNameNS(
                "urn:oasis:names:tc:SAML:protocol:ext:channel-binding", "ChannelBindings").getLength());
        assertEquals(1, document.getElementsByTagNameNS(
                "urn:ietf:params:xml:ns:samlec", "GeneratedKey").getLength());

        var header = (Element) document.getElementsByTagNameNS(
                "http://schemas.xmlsoap.org/soap/envelope/", "Header").item(0);
        while (header.hasChildNodes()) header.removeChild(header.getFirstChild());
        assertEquals(0, header.getChildNodes().getLength(), "ECP-to-IdP forwarding can remove every SP header");
        assertEquals(1, document.getElementsByTagNameNS(
                "urn:ietf:params:xml:ns:samlec", "GeneratedKey").getLength());
    }

    @Test
    void s2SloMessagesAndSessionDestructionOrderAreRepresentable() throws Exception {
        var xml = """
                <samlp:LogoutRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:aslo="urn:oasis:names:tc:SAML:2.0:protocol:ext:async-slo"
                  ID="_logout" Version="2.0" IssueInstant="2026-08-29T00:00:00Z">
                  <samlp:Extensions><aslo:Asynchronous/></samlp:Extensions>
                </samlp:LogoutRequest>
                """.getBytes(StandardCharsets.UTF_8);
        var document = SecureXml.parse(xml);
        assertEquals("LogoutRequest", document.getDocumentElement().getLocalName());
        assertEquals(1, document.getElementsByTagNameNS(
                "urn:oasis:names:tc:SAML:2.0:protocol:ext:async-slo", "Asynchronous").getLength());
        assertTrue(validLogoutOrder(List.of(
                "SESSION_ESTABLISHED", "LOGOUT_SENT", "RESPONSE_OBSERVED", "SESSION_DESTROYED")));
        assertFalse(validLogoutOrder(List.of(
                "SESSION_ESTABLISHED", "SESSION_DESTROYED", "LOGOUT_SENT", "RESPONSE_OBSERVED")));
    }

    @Test
    void s3MdqVariantsSurviveRequiredRedirectStatuses() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/variant", exchange -> {
            var body = exchange.getRequestURI().getRawQuery().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        for (var status : List.of(301, 302, 307)) {
            server.createContext("/r" + status, exchange -> {
                exchange.getResponseHeaders().add("Location", "/variant?variant=expired-valid-until");
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
            });
        }
        server.start();
        try {
            var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
            for (var status : List.of(301, 302, 307)) {
                var response = client.send(HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/r" + status)).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertEquals("variant=expired-valid-until", response.body());
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void s4SecondaryPeerHasIndependentEntityIdAndRegistrationPath() {
        var base = URI.create("https://peer.example.test/");
        var primary = base.resolve("/p/plan-1/idp");
        var secondary = base.resolve("/p/plan-1/idp/secondary");
        assertNotEquals(primary, secondary);
        assertEquals("https://peer.example.test/p/plan-1/idp/secondary/metadata", secondary + "/metadata");
    }

    @Test
    void s5RawXmlFixturesPreserveExactBytesAndXmlCharacterReferences() {
        RawMessageBuilder builder = specification -> specification.baseDocument().clone();
        var value = "x".repeat(255) + "\uD83D\uDE00";
        var raw = ("<!DOCTYPE x [<!ENTITY e 'ok'>]><x unknown=\"yes\" value=\"&#x9;\">"
                + value + "&e;</x>").getBytes(StandardCharsets.UTF_8);
        var built = builder.build(new RawMessageBuilder.RawMessageSpec("g2-raw", raw));
        assertArrayEquals(raw, built);
        assertTrue(new String(built, StandardCharsets.UTF_8).contains("&#x9;"));
        assertEquals(256, value.codePointCount(0, value.length()));
    }

    @Test
    void s6RedirectSignatureInputUsesRawEncodedOctets() {
        var raw = "unrelated=1&SAMLRequest=a%2Fb%2Bc&RelayState=x%20y%2Fz&SigAlg=http%3A%2F%2Falg&Signature=sig";
        var signed = new String(RedirectSignatureInput.fromRawQuery(raw), StandardCharsets.US_ASCII);
        assertEquals("SAMLRequest=a%2Fb%2Bc&RelayState=x%20y%2Fz&SigAlg=http%3A%2F%2Falg", signed);
        assertFalse(signed.contains("a/b+c"));
        assertThrows(RuntimeException.class, () -> RedirectSignatureInput.fromRawQuery(
                "SAMLRequest=a&SAMLRequest=b&SigAlg=alg"));
        assertThrows(RuntimeException.class, () -> RedirectSignatureInput.fromRawQuery(
                "SAMLRequest=a&SigAlg=alg&SAMLResponse=b"));
        assertThrows(RuntimeException.class, () -> RedirectSignatureInput.fromRawQuery(
                "SAMLRequest=a&SigAlg"));
        assertThrows(RuntimeException.class, () -> RedirectSignatureInput.fromRawQuery(
                "SAMLRequest=é&SigAlg=alg"));
    }

    private boolean validLogoutOrder(List<String> events) {
        return events.indexOf("SESSION_ESTABLISHED") < events.indexOf("LOGOUT_SENT")
                && events.indexOf("LOGOUT_SENT") < events.indexOf("RESPONSE_OBSERVED")
                && events.indexOf("RESPONSE_OBSERVED") < events.indexOf("SESSION_DESTROYED");
    }
}
