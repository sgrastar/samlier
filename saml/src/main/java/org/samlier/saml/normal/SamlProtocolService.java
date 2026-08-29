package org.samlier.saml.normal;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import javax.xml.XMLConstants;
import org.samlier.core.Identifiers;
import org.samlier.core.plan.TestPlan;
import org.samlier.saml.crypto.FilePlanKeyStore;
import org.samlier.saml.crypto.XmlSigner;
import org.samlier.saml.metadata.MetadataService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class SamlProtocolService {
    private static final int MAX_XML_BYTES = 5 * 1024 * 1024;
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String SUCCESS = "urn:oasis:names:tc:SAML:2.0:status:Success";
    private static final String BEARER = "urn:oasis:names:tc:SAML:2.0:cm:bearer";
    private static final String TRANSIENT = "urn:oasis:names:tc:SAML:2.0:nameid-format:transient";

    private final URI peerBase;
    private final FilePlanKeyStore keyStore;
    private final XmlSigner signer;
    private final OpenSamlReader reader;
    private final Clock clock;

    public SamlProtocolService(URI peerBase, FilePlanKeyStore keyStore, XmlSigner signer,
                               OpenSamlReader reader, Clock clock) {
        this.peerBase = peerBase;
        this.keyStore = keyStore;
        this.signer = signer;
        this.reader = reader;
        this.clock = clock;
    }

    public AuthnRequestMessage buildAuthnRequest(TestPlan plan, URI destination, String relayState) {
        var document = SecureXml.newDocument();
        var request = element(document, PROTOCOL, "samlp:AuthnRequest");
        declareSamlNamespaces(request);
        var id = "_" + Identifiers.newId("saml");
        request.setAttribute("ID", id);
        request.setAttribute("Version", "2.0");
        request.setAttribute("IssueInstant", instant(clock.instant()));
        request.setAttribute("Destination", destination.toString());
        request.setAttribute("AssertionConsumerServiceURL", peerEndpoint(plan, "/sp/acs/0"));
        request.setAttribute("ProtocolBinding", MetadataService.POST);
        var issuer = element(document, ASSERTION, "saml:Issuer");
        issuer.setTextContent(peerEndpoint(plan, ""));
        request.appendChild(issuer);
        document.appendChild(request);
        var xml = SecureXml.serialize(document);
        reader.read(xml);
        var encoded = url(Base64.getEncoder().encodeToString(deflate(xml)));
        var redirect = URI.create(destination + (destination.toString().contains("?") ? "&" : "?")
                + "SAMLRequest=" + encoded + "&RelayState=" + url(relayState));
        return new AuthnRequestMessage(id, xml, redirect, relayState);
    }

    public DecodedMessage decodeRedirect(String rawQuery, String parameter) {
        return parse(decodeRedirectRaw(rawQuery, parameter));
    }

    public RawDecodedMessage decodeRedirectRaw(String rawQuery, String parameter) {
        var parameters = parseForm(rawQuery);
        var value = parameters.get(parameter);
        if (value == null) throw new SamlException("Missing " + parameter);
        try {
            var compressed = Base64.getDecoder().decode(value);
            var inflater = new Inflater(true);
            try (var input = new InflaterInputStream(new java.io.ByteArrayInputStream(compressed), inflater)) {
                var xml = input.readNBytes(MAX_XML_BYTES + 1);
                if (xml.length > MAX_XML_BYTES) throw new SamlException("Decoded SAML message exceeds the 5 MiB limit");
                return new RawDecodedMessage(xml, parameters.get("RelayState"));
            } finally {
                inflater.end();
            }
        } catch (Exception e) {
            throw new SamlException("Could not decode HTTP-Redirect SAML message", e);
        }
    }

    public DecodedMessage decodePost(byte[] rawBody, String parameter) {
        return parse(decodePostRaw(rawBody, parameter));
    }

    public RawDecodedMessage decodePostRaw(byte[] rawBody, String parameter) {
        var parameters = parseForm(new String(rawBody, StandardCharsets.UTF_8));
        var value = parameters.get(parameter);
        if (value == null) throw new SamlException("Missing " + parameter);
        try {
            var xml = Base64.getDecoder().decode(value);
            if (xml.length > MAX_XML_BYTES) throw new SamlException("Decoded SAML message exceeds the 5 MiB limit");
            return new RawDecodedMessage(xml, parameters.get("RelayState"));
        } catch (IllegalArgumentException e) {
            throw new SamlException("Could not decode HTTP-POST SAML message", e);
        }
    }

    public DecodedMessage parse(RawDecodedMessage message) {
        return decoded(message.xml(), message.relayState());
    }

    public ResponseMessage buildResponse(TestPlan plan, DecodedMessage request, URI acs, String subjectValue) {
        var requestRoot = request.parsed().document().getDocumentElement();
        var requestId = requestRoot.getAttribute("ID");
        var now = clock.instant();
        var expiry = now.plus(Duration.ofMinutes(5));
        var document = SecureXml.newDocument();
        var response = element(document, PROTOCOL, "samlp:Response");
        declareSamlNamespaces(response);
        response.setAttribute("ID", "_" + Identifiers.newId("saml"));
        response.setAttribute("Version", "2.0");
        response.setAttribute("IssueInstant", instant(now));
        response.setAttribute("Destination", acs.toString());
        response.setAttribute("InResponseTo", requestId);
        document.appendChild(response);

        var issuer = element(document, ASSERTION, "saml:Issuer");
        issuer.setTextContent(peerEndpoint(plan, ""));
        response.appendChild(issuer);
        var status = element(document, PROTOCOL, "samlp:Status");
        var statusCode = element(document, PROTOCOL, "samlp:StatusCode");
        statusCode.setAttribute("Value", SUCCESS);
        status.appendChild(statusCode);
        response.appendChild(status);

        var assertion = element(document, ASSERTION, "saml:Assertion");
        assertion.setAttribute("ID", "_" + Identifiers.newId("saml"));
        assertion.setAttribute("Version", "2.0");
        assertion.setAttribute("IssueInstant", instant(now));
        var assertionIssuer = element(document, ASSERTION, "saml:Issuer");
        assertionIssuer.setTextContent(peerEndpoint(plan, ""));
        assertion.appendChild(assertionIssuer);

        var subject = element(document, ASSERTION, "saml:Subject");
        var nameId = element(document, ASSERTION, "saml:NameID");
        nameId.setAttribute("Format", TRANSIENT);
        nameId.setTextContent(subjectValue);
        subject.appendChild(nameId);
        var confirmation = element(document, ASSERTION, "saml:SubjectConfirmation");
        confirmation.setAttribute("Method", BEARER);
        var confirmationData = element(document, ASSERTION, "saml:SubjectConfirmationData");
        confirmationData.setAttribute("Recipient", acs.toString());
        confirmationData.setAttribute("InResponseTo", requestId);
        confirmationData.setAttribute("NotOnOrAfter", instant(expiry));
        confirmation.appendChild(confirmationData);
        subject.appendChild(confirmation);
        assertion.appendChild(subject);

        var conditions = element(document, ASSERTION, "saml:Conditions");
        conditions.setAttribute("NotBefore", instant(now.minus(Duration.ofMinutes(1))));
        conditions.setAttribute("NotOnOrAfter", instant(expiry));
        var audienceRestriction = element(document, ASSERTION, "saml:AudienceRestriction");
        var audience = element(document, ASSERTION, "saml:Audience");
        audience.setTextContent(plan.target().entityId());
        audienceRestriction.appendChild(audience);
        conditions.appendChild(audienceRestriction);
        assertion.appendChild(conditions);

        var authnStatement = element(document, ASSERTION, "saml:AuthnStatement");
        authnStatement.setAttribute("AuthnInstant", instant(now));
        authnStatement.setAttribute("SessionIndex", "_" + Identifiers.newId("session"));
        var authnContext = element(document, ASSERTION, "saml:AuthnContext");
        var classRef = element(document, ASSERTION, "saml:AuthnContextClassRef");
        classRef.setTextContent("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport");
        authnContext.appendChild(classRef);
        authnStatement.appendChild(authnContext);
        assertion.appendChild(authnStatement);
        response.appendChild(assertion);

        signer.sign(assertion, keyStore.getOrCreate(plan.id()), subject);
        var xml = SecureXml.serialize(document);
        reader.read(xml);
        return new ResponseMessage(response.getAttribute("ID"), xml,
                Base64.getEncoder().encodeToString(xml), acs, request.relayState());
    }

    private DecodedMessage decoded(byte[] xml, String relayState) {
        var parsed = reader.read(xml);
        return new DecodedMessage(xml, parsed, relayState);
    }

    private byte[] deflate(byte[] value) {
        try {
            var output = new ByteArrayOutputStream();
            var deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
            try (var stream = new DeflaterOutputStream(output, deflater)) {
                stream.write(value);
            } finally {
                deflater.end();
            }
            return output.toByteArray();
        } catch (Exception e) {
            throw new SamlException("Could not DEFLATE AuthnRequest", e);
        }
    }

    private Map<String, String> parseForm(String encoded) {
        var values = new LinkedHashMap<String, String>();
        if (encoded == null) return values;
        for (var part : encoded.split("&")) {
            var equals = part.indexOf('=');
            var key = URLDecoder.decode(equals < 0 ? part : part.substring(0, equals), StandardCharsets.UTF_8);
            var value = URLDecoder.decode(equals < 0 ? "" : part.substring(equals + 1), StandardCharsets.UTF_8);
            values.putIfAbsent(key, value);
        }
        return values;
    }

    private String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String peerEndpoint(TestPlan plan, String suffix) {
        return peerBase.resolve("/p/" + plan.id() + suffix).toString();
    }

    private String instant(java.time.Instant value) {
        return DateTimeFormatter.ISO_INSTANT.format(value);
    }

    private Element element(Document document, String namespace, String qualifiedName) {
        return document.createElementNS(namespace, qualifiedName);
    }

    private void declareSamlNamespaces(Element root) {
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:samlp", PROTOCOL);
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:saml", ASSERTION);
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:ds", MetadataService.DS);
    }

    public record AuthnRequestMessage(String id, byte[] xml, URI redirect, String relayState) {}
    public record RawDecodedMessage(byte[] xml, String relayState) {
        public RawDecodedMessage { xml = xml.clone(); }
        @Override public byte[] xml() { return xml.clone(); }
    }
    public record DecodedMessage(byte[] xml, OpenSamlReader.ParsedMessage parsed, String relayState) {}
    public record ResponseMessage(String id, byte[] xml, String base64, URI destination, String relayState) {}
}
