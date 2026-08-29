package org.samlier.peer.logout;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.samlier.core.plan.PlanRepository;
import org.samlier.core.run.RunRepository;
import org.samlier.core.transcript.Direction;
import org.samlier.core.transcript.TranscriptInput;
import org.samlier.core.transcript.TranscriptRecorder;
import org.samlier.saml.metadata.MetadataService;
import org.samlier.saml.metadata.TargetMetadataParser;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SamlProtocolService;
import org.samlier.saml.normal.SecureXml;
import org.samlier.store.MetadataCache;
import org.w3c.dom.Element;

/** Role-neutral SLO receiving endpoint for the Suite SP and Suite IdP. */
public final class SloPeerService {
    public enum Transport { FRONT_CHANNEL, SOAP }
    public record Result(
            String runId,
            String messageType,
            SamlProtocolService.ResponseMessage response,
            String responseBinding) {}

    private final PlanRepository plans;
    private final RunRepository runs;
    private final MetadataCache metadata;
    private final TargetMetadataParser parser;
    private final SamlProtocolService saml;
    private final TranscriptRecorder transcript;
    private final Clock clock;

    public SloPeerService(
            PlanRepository plans,
            RunRepository runs,
            MetadataCache metadata,
            TargetMetadataParser parser,
            SamlProtocolService saml,
            TranscriptRecorder transcript,
            Clock clock) {
        this.plans = java.util.Objects.requireNonNull(plans, "plans");
        this.runs = java.util.Objects.requireNonNull(runs, "runs");
        this.metadata = java.util.Objects.requireNonNull(metadata, "metadata");
        this.parser = java.util.Objects.requireNonNull(parser, "parser");
        this.saml = java.util.Objects.requireNonNull(saml, "saml");
        this.transcript = java.util.Objects.requireNonNull(transcript, "transcript");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    public Result consume(
            String planId,
            Transport transport,
            String method,
            String rawQuery,
            byte[] rawBody,
            Map<String, List<String>> headers,
            String requestUrl) {
        var plan = plans.find(planId).orElseThrow(() -> new IllegalArgumentException("Unknown Test Plan"));
        var decoded = transport == Transport.SOAP ? decodeSoap(rawBody) : decodeFront(method, rawQuery, rawBody);
        var runId = queryParameter(requestUrl, "run");
        if (runId == null) runId = decoded.message().relayState();
        if (runId == null || runId.isBlank()) throw new SamlException("SLO message has no Run correlation");
        var run = runs.find(runId).orElseThrow(() -> new SamlException("Unknown correlated Run"));
        if (!planId.equals(run.planId())) throw new SamlException("Correlated Run belongs to another Test Plan");

        var parsed = saml.parse(decoded.message());
        var root = parsed.parsed().document().getDocumentElement();
        var messageType = root.getLocalName();
        if (!"LogoutRequest".equals(messageType) && !"LogoutResponse".equals(messageType)) {
            throw new SamlException("Not a SAML logout message");
        }
        var transcriptXml = transport == Transport.SOAP ? rawBody : decoded.message().xml();
        transcript.record(new TranscriptInput(
                run.id(), Direction.INBOUND, clock.instant(), root.getAttribute("ID"), method,
                requestUrl, 200, headers, rawBody,
                transport == Transport.SOAP ? "text/xml" : contentType(method),
                rawQuery, transcriptXml, Map.of("type", messageType, "transport", transport.name())));
        if ("LogoutResponse".equals(messageType)) return new Result(run.id(), messageType, null, null);

        var target = parser.parse(metadata.get(plan.id()), plan.target().entityId());
        var preferredBinding = transport == Transport.SOAP ? MetadataService.SOAP
                : "GET".equalsIgnoreCase(method) ? MetadataService.REDIRECT : MetadataService.POST;
        var endpoint = target.singleLogoutServices().stream()
                .filter(value -> preferredBinding.equals(value.binding())).findFirst()
                .or(() -> target.singleLogoutServices().stream().findFirst())
                .orElse(null);
        if (endpoint == null) return new Result(run.id(), messageType, null, null);
        var response = saml.buildLogoutResponse(plan, parsed, endpoint.location());
        transcript.record(new TranscriptInput(
                run.id(), Direction.OUTBOUND, clock.instant(), response.id(), "POST",
                endpoint.location().toString(), null, Map.of(), new byte[0],
                transport == Transport.SOAP ? "text/xml" : "application/x-www-form-urlencoded",
                null, transport == Transport.SOAP ? soap(response.xml()) : response.xml(),
                Map.of("type", "LogoutResponse", "transport", transport.name(),
                        "binding", endpoint.binding())));
        return new Result(run.id(), messageType, response, endpoint.binding());
    }

    public URI redirectResponse(Result result) {
        requireResponse(result);
        return saml.redirectResponse(result.response());
    }

    public byte[] soapResponse(Result result) {
        requireResponse(result);
        return soap(result.response().xml());
    }

    private void requireResponse(Result result) {
        if (result == null || result.response() == null) {
            throw new IllegalArgumentException("SLO result has no response");
        }
    }

    private Decoded decodeFront(String method, String rawQuery, byte[] rawBody) {
        for (var parameter : List.of("SAMLRequest", "SAMLResponse")) {
            try {
                var value = "GET".equalsIgnoreCase(method)
                        ? saml.decodeRedirectRaw(rawQuery, parameter)
                        : saml.decodePostRaw(rawBody, parameter);
                return new Decoded(value);
            } catch (SamlException ignored) {
                // Try the other legal message parameter.
            }
        }
        throw new SamlException("SLO transport has no decodable SAMLRequest or SAMLResponse");
    }

    private Decoded decodeSoap(byte[] body) {
        var document = SecureXml.parse(body);
        for (var name : List.of("LogoutRequest", "LogoutResponse")) {
            var nodes = document.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:protocol", name);
            if (nodes.getLength() == 0) continue;
            var inner = SecureXml.newDocument();
            inner.appendChild(inner.importNode((Element) nodes.item(0), true));
            return new Decoded(new SamlProtocolService.RawDecodedMessage(SecureXml.serialize(inner), null));
        }
        throw new SamlException("SOAP body has no LogoutRequest or LogoutResponse");
    }

    private byte[] soap(byte[] message) {
        var messageDocument = SecureXml.parse(message);
        var document = SecureXml.newDocument();
        var envelope = document.createElementNS("http://schemas.xmlsoap.org/soap/envelope/", "S:Envelope");
        envelope.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:S",
                "http://schemas.xmlsoap.org/soap/envelope/");
        var body = document.createElementNS("http://schemas.xmlsoap.org/soap/envelope/", "S:Body");
        body.appendChild(document.importNode(messageDocument.getDocumentElement(), true));
        envelope.appendChild(body); document.appendChild(envelope);
        return SecureXml.serialize(document);
    }

    private String queryParameter(String requestUrl, String name) {
        var query = URI.create(requestUrl).getRawQuery();
        if (query == null) return null;
        for (var part : query.split("&")) {
            var separator = part.indexOf('=');
            var key = separator < 0 ? part : part.substring(0, separator);
            if (name.equals(java.net.URLDecoder.decode(key, StandardCharsets.UTF_8))) {
                return separator < 0 ? "" : java.net.URLDecoder.decode(
                        part.substring(separator + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
    private String contentType(String method) {
        return "GET".equalsIgnoreCase(method) ? null : "application/x-www-form-urlencoded";
    }
    private record Decoded(SamlProtocolService.RawDecodedMessage message) {}
}
