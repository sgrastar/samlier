package org.samlier.runner.cases;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.transcript.Direction;
import org.samlier.core.transcript.TranscriptContentReader;
import org.samlier.core.transcript.TranscriptEntry;
import org.samlier.core.transcript.TranscriptRecorder;
import org.samlier.saml.crypto.XmlSignatureVerifier;
import org.samlier.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** Passive evidence rules for ECP and the separately versioned SAML-EC extension. */
public final class EcpTranscriptProfileCase {
    public enum Rule {
        BASIC_EXCHANGE, RESPONSE_OR_FAULT, RESPONSE_HEADER, REQUEST_AUTHENTICATED,
        HEADER_ATTRIBUTES, RESPONSE_INTEGRITY, RELAY_STATE_CHOICE, DELEGATION_CHOICE,
        INTERMEDIATE_EXCHANGES, BEARER_CONFIRMATION, CHANNEL_BINDINGS, HTTP_BASIC,
        GENERATED_KEY
    }

    private static final String SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String ECP = "urn:oasis:names:tc:SAML:2.0:profiles:SSO:ecp";
    private static final String PAOS = "urn:liberty:paos:2003-08";
    private static final String CB = "urn:oasis:names:tc:SAML:protocol:ext:channel-binding";
    private static final String SAML_EC = "urn:ietf:params:xml:ns:samlec";
    private static final String BEARER = "urn:oasis:names:tc:SAML:2.0:cm:bearer";
    private static final String NEXT = "http://schemas.xmlsoap.org/soap/actor/next";
    private final Rule rule;
    private final List<X509Certificate> verificationKeys;
    private final XmlSignatureVerifier verifier = new XmlSignatureVerifier();

    public EcpTranscriptProfileCase(Rule rule, List<X509Certificate> verificationKeys) {
        this.rule = java.util.Objects.requireNonNull(rule, "rule");
        this.verificationKeys = List.copyOf(verificationKeys == null ? List.of() : verificationKeys);
    }

    public CaseOutcome evaluate(String runId, TranscriptRecorder recorder, TranscriptContentReader content) {
        var exchanges = envelopes(runId, recorder, content);
        var requests = exchanges.stream().filter(value -> value.entry().direction() == Direction.OUTBOUND).toList();
        var responses = exchanges.stream().filter(value -> value.entry().direction() == Direction.INBOUND).toList();
        return switch (rule) {
            case BASIC_EXCHANGE, RESPONSE_OR_FAULT -> exchangeCompletion(requests, responses);
            case RESPONSE_HEADER -> responseHeader(requests, responses);
            case REQUEST_AUTHENTICATED -> requestAuthenticated(requests, responses);
            case HEADER_ATTRIBUTES -> headerAttributes(responses);
            case RESPONSE_INTEGRITY -> integrity(responses);
            case RELAY_STATE_CHOICE -> informational(responses, "ecp.relay-state.choice");
            case DELEGATION_CHOICE -> informational(responses, "ecp.delegation.choice");
            case INTERMEDIATE_EXCHANGES -> informational(exchanges, "ecp.intermediate-exchange.choice");
            case BEARER_CONFIRMATION -> bearer(requests, responses);
            case CHANNEL_BINDINGS -> channelBindings(requests, responses);
            case HTTP_BASIC -> httpBasic(requests, responses);
            case GENERATED_KEY -> generatedKey(responses);
        };
    }

    private CaseOutcome exchangeCompletion(List<Envelope> requests, List<Envelope> responses) {
        if (requests.isEmpty() || responses.isEmpty()) return notVerified("ecp_exchange_not_observed");
        var invalid = responses.stream().filter(value -> !has(value.document(), PROTOCOL, "Response")
                && !has(value.document(), SOAP, "Fault")).map(Envelope::reference).toList();
        return outcome(responses, invalid, "ecp.exchange");
    }

    private CaseOutcome responseHeader(List<Envelope> requests, List<Envelope> responses) {
        if (responses.isEmpty()) return notVerified("ecp_response_not_observed");
        var expected = responseConsumer(requests);
        var scoped = responses.stream().filter(value -> has(value.document(), PROTOCOL, "Response")).toList();
        if (scoped.isEmpty()) return optional("ecp.soap-fault-observed");
        var invalid = new ArrayList<String>();
        for (var response : scoped) {
            var header = first(response.document(), ECP, "Response");
            if (header == null || header.getAttribute("AssertionConsumerServiceURL").isBlank()
                    || (expected != null && !expected.equals(header.getAttribute("AssertionConsumerServiceURL")))) {
                invalid.add(response.reference());
            }
        }
        return outcome(scoped, invalid, "ecp.response-header");
    }

    private CaseOutcome requestAuthenticated(List<Envelope> requests, List<Envelope> responses) {
        var signed = requests.stream().anyMatch(value -> has(value.document(),
                "http://www.w3.org/2000/09/xmldsig#", "Signature"));
        if (!signed) return optional("ecp.signed-request.not-observed");
        var scoped = responses.stream().filter(value -> has(value.document(), PROTOCOL, "Response")).toList();
        if (scoped.isEmpty()) return notVerified("ecp_response_not_observed");
        var invalid = scoped.stream().filter(value -> !has(value.document(), ECP, "RequestAuthenticated"))
                .map(Envelope::reference).toList();
        return outcome(scoped, invalid, "ecp.request-authenticated");
    }

    private CaseOutcome headerAttributes(List<Envelope> responses) {
        if (responses.isEmpty()) return notVerified("ecp_response_not_observed");
        var invalid = new ArrayList<String>();
        var observed = 0;
        for (var response : responses) {
            for (var name : List.of("Response", "RequestAuthenticated", "RelayState")) {
                var namespace = "RelayState".equals(name) ? ECP : ECP;
                var headers = response.document().getElementsByTagNameNS(namespace, name);
                for (var index = 0; index < headers.getLength(); index++) {
                    observed++;
                    var header = (Element) headers.item(index);
                    if (!NEXT.equals(header.getAttributeNS(SOAP, "actor"))) invalid.add(response.reference() + "#" + name);
                    if (("Response".equals(name) || "RelayState".equals(name))
                            && !"1".equals(header.getAttributeNS(SOAP, "mustUnderstand"))) {
                        invalid.add(response.reference() + "#" + name + "-mustUnderstand");
                    }
                }
            }
            var bindings = response.document().getElementsByTagNameNS(CB, "ChannelBindings");
            for (var index = 0; index < bindings.getLength(); index++) {
                var binding = (Element) bindings.item(index);
                if (ancestor(binding, SOAP, "Header")) {
                    observed++;
                    if (!NEXT.equals(binding.getAttributeNS(SOAP, "actor"))
                            || !"1".equals(binding.getAttributeNS(SOAP, "mustUnderstand"))) {
                        invalid.add(response.reference() + "#ChannelBindings");
                    }
                }
            }
        }
        if (observed == 0) return notVerified("ecp_headers_not_observed");
        return outcome(responses, invalid, "ecp.header-attributes");
    }

    private CaseOutcome integrity(List<Envelope> responses) {
        var scoped = responses.stream().filter(value -> has(value.document(), PROTOCOL, "Response")).toList();
        if (scoped.isEmpty()) return notVerified("ecp_response_not_observed");
        if (verificationKeys.isEmpty()) return notVerified("target_signing_key_unavailable");
        var invalid = new ArrayList<String>();
        for (var response : scoped) {
            var assertions = response.document().getElementsByTagNameNS(ASSERTION, "Assertion");
            for (var index = 0; index < assertions.getLength(); index++) {
                var assertion = (Element) assertions.item(index);
                var protocolResponse = ancestorElement(assertion, PROTOCOL, "Response");
                var assertionValid = valid(assertion);
                var responseValid = protocolResponse != null && valid(protocolResponse);
                if (!assertionValid && !responseValid) invalid.add(response.reference() + "#Assertion[" + index + "]");
            }
            if (assertions.getLength() == 0) invalid.add(response.reference() + "#no-assertion");
        }
        return outcome(scoped, invalid, "ecp.response-integrity");
    }

    private CaseOutcome bearer(List<Envelope> requests, List<Envelope> responses) {
        var expected = responseConsumer(requests);
        var confirmations = new ArrayList<Element>();
        responses.forEach(value -> {
            var nodes = value.document().getElementsByTagNameNS(ASSERTION, "SubjectConfirmation");
            for (var index = 0; index < nodes.getLength(); index++) confirmations.add((Element) nodes.item(index));
        });
        if (confirmations.isEmpty()) return notVerified("ecp_subject_confirmation_not_observed");
        var valid = confirmations.stream().anyMatch(value -> {
            if (!BEARER.equals(value.getAttribute("Method"))) return false;
            var data = direct(value, ASSERTION, "SubjectConfirmationData");
            return data != null && !data.getAttribute("Recipient").isBlank()
                    && (expected == null || expected.equals(data.getAttribute("Recipient")));
        });
        return outcome(responses, valid ? List.of() : List.of("bearer-confirmation-missing"), "ecp.bearer");
    }

    private CaseOutcome channelBindings(List<Envelope> requests, List<Envelope> responses) {
        var requestValues = bindingValues(requests);
        if (requestValues.isEmpty()) return notVerified("ecp_channel_binding_probe_not_observed");
        var scoped = responses.stream().filter(value -> has(value.document(), PROTOCOL, "Response")).toList();
        if (scoped.isEmpty()) return notVerified("ecp_channel_binding_response_not_observed");
        var invalid = new ArrayList<String>();
        for (var response : scoped) {
            var header = values(response.document(), CB, "ChannelBindings", true);
            var advice = values(response.document(), CB, "ChannelBindings", false);
            if (header.isEmpty() || advice.isEmpty()
                    || header.stream().noneMatch(requestValues::contains)
                    || advice.stream().noneMatch(requestValues::contains)) invalid.add(response.reference());
        }
        return outcome(scoped, invalid, "ecp.channel-bindings");
    }

    private CaseOutcome httpBasic(List<Envelope> requests, List<Envelope> responses) {
        var basic = requests.stream().anyMatch(value -> value.entry().headers().entrySet().stream()
                .anyMatch(header -> "authorization".equalsIgnoreCase(header.getKey())
                        && header.getValue().stream().anyMatch(item -> item.toLowerCase().contains("basic"))));
        if (!basic) return notVerified("ecp_http_basic_probe_not_observed");
        return exchangeCompletion(requests, responses);
    }

    private CaseOutcome generatedKey(List<Envelope> responses) {
        if (responses.isEmpty()) return notVerified("saml_ec_response_not_observed");
        var invalid = new ArrayList<String>();
        var observed = 0;
        for (var response : responses) {
            var keys = response.document().getElementsByTagNameNS(SAML_EC, "GeneratedKey");
            if (keys.getLength() == 0) continue;
            observed += keys.getLength();
            var headerCopy = false;
            var adviceCopy = false;
            for (var index = 0; index < keys.getLength(); index++) {
                var key = (Element) keys.item(index);
                headerCopy |= ancestor(key, SOAP, "Header");
                adviceCopy |= ancestor(key, ASSERTION, "Advice");
                try {
                    if (Base64.getDecoder().decode(key.getTextContent().strip()).length < 16) {
                        invalid.add(response.reference() + "#GeneratedKey-too-short");
                    }
                } catch (IllegalArgumentException malformed) {
                    invalid.add(response.reference() + "#GeneratedKey-not-base64");
                }
            }
            if (!headerCopy || !adviceCopy
                    || response.document().getElementsByTagNameNS(ASSERTION, "EncryptedAssertion").getLength() == 0) {
                invalid.add(response.reference() + "#GeneratedKey-placement-or-encryption");
            }
        }
        if (observed == 0) return notVerified("saml_ec_generated_key_not_observed");
        return outcome(responses, invalid, "saml-ec.generated-key");
    }

    private CaseOutcome informational(List<Envelope> messages, String code) {
        return new CaseOutcome(
                Outcome.SATISFIED_WITH_NOTE, null, code, code, evidence(messages),
                Map.of("observed_envelopes", messages.size()));
    }
    private CaseOutcome optional(String code) {
        return new CaseOutcome(Outcome.SATISFIED_WITH_NOTE, null, code, code, List.of(), Map.of());
    }
    private CaseOutcome notVerified(String reason) {
        return CaseOutcome.notVerified(reason, "ecp." + reason.replace('_', '-'));
    }
    private CaseOutcome outcome(List<Envelope> messages, List<String> violations, String code) {
        return new CaseOutcome(
                violations.isEmpty() ? Outcome.SATISFIED : Outcome.VIOLATED, null,
                violations.isEmpty() ? code + ".satisfied" : code + ".violated",
                violations.isEmpty() ? code + ".satisfied" : code + ".violated",
                evidence(messages), Map.of("observed", messages.size(), "violations", List.copyOf(violations)));
    }
    private List<EvidenceRef> evidence(List<Envelope> messages) {
        return messages.stream().map(value -> new EvidenceRef("transcript", value.reference())).distinct().toList();
    }
    private boolean valid(Element element) {
        return verificationKeys.stream().anyMatch(certificate -> verifier.hasValidEnvelopedSignature(element, certificate));
    }

    private List<String> bindingValues(List<Envelope> messages) {
        var result = new ArrayList<String>();
        messages.forEach(value -> {
            var nodes = value.document().getElementsByTagNameNS(CB, "ChannelBindings");
            for (var index = 0; index < nodes.getLength(); index++) {
                result.add(((Element) nodes.item(index)).getTextContent().strip());
            }
        });
        return result;
    }
    private String responseConsumer(List<Envelope> requests) {
        var paosValue = requests.stream().map(value -> first(value.document(), PAOS, "Request"))
                .filter(java.util.Objects::nonNull).map(value -> value.getAttribute("responseConsumerURL"))
                .filter(value -> !value.isBlank()).findFirst();
        if (paosValue.isPresent()) return paosValue.orElseThrow();
        return requests.stream().map(value -> first(value.document(), PROTOCOL, "AuthnRequest"))
                .filter(java.util.Objects::nonNull)
                .map(value -> value.getAttribute("AssertionConsumerServiceURL"))
                .filter(value -> !value.isBlank()).findFirst().orElse(null);
    }
    private List<String> values(org.w3c.dom.Document document, String namespace, String name, boolean headerOnly) {
        var result = new ArrayList<String>();
        var nodes = document.getElementsByTagNameNS(namespace, name);
        for (var index = 0; index < nodes.getLength(); index++) {
            var element = (Element) nodes.item(index);
            if (headerOnly == ancestor(element, SOAP, "Header")) result.add(element.getTextContent().strip());
        }
        return result;
    }

    private List<Envelope> envelopes(String runId, TranscriptRecorder recorder, TranscriptContentReader content) {
        var result = new ArrayList<Envelope>();
        for (var entry : recorder.list(runId)) {
            if (entry.decodedSamlRef() == null || entry.decodedSamlBytes() == 0) continue;
            try {
                var document = SecureXml.parse(content.readDecodedSaml(entry));
                if (SOAP.equals(document.getDocumentElement().getNamespaceURI())
                        && "Envelope".equals(document.getDocumentElement().getLocalName())) {
                    result.add(new Envelope(entry, document, "transcript:" + entry.id()));
                }
            } catch (RuntimeException ignored) {
                // Non-ECP or malformed XML belongs to another case.
            }
        }
        return List.copyOf(result);
    }

    private boolean has(org.w3c.dom.Document document, String namespace, String name) {
        return document.getElementsByTagNameNS(namespace, name).getLength() > 0;
    }
    private Element first(org.w3c.dom.Document document, String namespace, String name) {
        var nodes = document.getElementsByTagNameNS(namespace, name);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }
    private Element direct(Element parent, String namespace, String name) {
        if (parent == null) return null;
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && namespace.equals(element.getNamespaceURI())
                    && name.equals(element.getLocalName())) return element;
        }
        return null;
    }
    private boolean ancestor(Element element, String namespace, String name) {
        return ancestorElement(element, namespace, name) != null;
    }
    private Element ancestorElement(Element element, String namespace, String name) {
        for (var node = element.getParentNode(); node != null; node = node.getParentNode()) {
            if (node instanceof Element parent && namespace.equals(parent.getNamespaceURI())
                    && name.equals(parent.getLocalName())) return parent;
        }
        return null;
    }
    private record Envelope(TranscriptEntry entry, org.w3c.dom.Document document, String reference) {}
}
