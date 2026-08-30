package org.samlier.runner.cases;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SecureXml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Product-neutral oracles that need only an ordinary AuthnRequest/Response transcript. */
final class NormalFlowBrowserObservation {
    static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    static final String SUCCESS = "urn:oasis:names:tc:SAML:2.0:status:Success";
    static final String BEARER = "urn:oasis:names:tc:SAML:2.0:cm:bearer";
    static final String PERSISTENT = "urn:oasis:names:tc:SAML:2.0:nameid-format:persistent";
    static final String TRANSIENT = "urn:oasis:names:tc:SAML:2.0:nameid-format:transient";

    private NormalFlowBrowserObservation() {}

    static Optional<CaseOutcome> evaluate(String caseId, List<Message> messages) {
        return evaluate(caseId, messages, null);
    }

    static Optional<CaseOutcome> evaluate(
            String caseId, List<Message> messages, String expectedTargetEntityId) {
        var parsed = parse(messages);
        if (parsed.isEmpty() || parsed.size() != (messages == null ? 0 : messages.size())) {
            return Optional.empty();
        }
        return switch (caseId) {
            case "IIP-SSO01-i1-idp-01" -> sameAssertionIssuer(parsed);
            case "IIP-SSO01-j-idp-01" -> everyAssertionHasBearerConfirmation(parsed);
            case "IIP-SSO01-k1-idp-01" -> noBearerNotBefore(parsed);
            case "IIP-SSO01-k2-idp-01" -> correlatedResponses(parsed, true);
            case "IIP-SSO01-m-idp-01" -> everyBearerAssertionHasAudience(parsed);
            case "IIP-SSO01-x-idp-01" -> noRedirectResponse(parsed);
            case "IIP-SSO01-ap-idp-01" -> correlatedResponses(parsed, false);
            case "IIP-SSO01-ad-idp-01" -> exchangesUseTls(parsed);
            case "IIP-SSO01-en-idp-01" -> responseVersionNotHigher(parsed);
            case "IIP-SSO01-h-idp-01" -> responseIssuer(parsed, expectedTargetEntityId);
            case "IIP-SSO03-a-idp-01" -> successfulPostResponse(parsed);
            case "IIP-SSO05-a2-idp-01" -> nameIdLength(parsed, PERSISTENT, "persistent");
            case "IIP-SSO05-b1-idp-01" -> nameIdLength(parsed, TRANSIENT, "transient");
            case "IIP-IDP09-b-idp-01" -> encryptionChoice(parsed);
            default -> Optional.empty();
        };
    }

    static boolean supports(String caseId) {
        return switch (caseId) {
            case "IIP-SSO01-i1-idp-01", "IIP-SSO01-j-idp-01", "IIP-SSO01-k1-idp-01",
                    "IIP-SSO01-k2-idp-01", "IIP-SSO01-m-idp-01", "IIP-SSO01-x-idp-01",
                    "IIP-SSO01-ad-idp-01", "IIP-SSO01-ap-idp-01", "IIP-SSO01-en-idp-01",
                    "IIP-SSO01-h-idp-01",
                    "IIP-SSO03-a-idp-01",
                    "IIP-SSO05-a2-idp-01", "IIP-SSO05-b1-idp-01", "IIP-IDP09-b-idp-01" -> true;
            default -> false;
        };
    }

    private static Optional<CaseOutcome> responseIssuer(
            List<Parsed> messages, String expectedTargetEntityId) {
        if (expectedTargetEntityId == null || expectedTargetEntityId.isBlank()) return Optional.empty();
        var responses = responses(messages);
        if (responses.isEmpty()) return Optional.empty();
        var evidence = new ArrayList<EvidenceRef>();
        var present = 0;
        for (var response : responses) {
            evidence.add(response.evidence());
            var issuer = direct(response.document().getDocumentElement(), ASSERTION, "Issuer");
            if (issuer == null) continue; // The profile explicitly permits omission.
            present++;
            var format = issuer.getAttribute("Format");
            if ((!format.isBlank() && !"urn:oasis:names:tc:SAML:2.0:nameid-format:entity".equals(format))
                    || !expectedTargetEntityId.equals(issuer.getTextContent())) {
                return Optional.of(outcome(
                        Outcome.VIOLATED, "browser.normal-flow.response-issuer-invalid",
                        evidence, Map.of(
                                "issuers_present", present,
                                "expected_entity_id", expectedTargetEntityId,
                                "actual_entity_id", issuer.getTextContent(),
                                "format", format)));
            }
        }
        return Optional.of(outcome(
                present == 0 ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED,
                present == 0
                        ? "browser.normal-flow.response-issuer-omitted"
                        : "browser.normal-flow.response-issuer-valid",
                evidence, Map.of("responses", responses.size(), "issuers_present", present)));
    }

    private static Optional<CaseOutcome> sameAssertionIssuer(List<Parsed> messages) {
        var inspected = new ArrayList<EvidenceRef>();
        var observedAssertions = 0;
        var observedMultipleAssertions = false;
        for (var message : responses(messages)) {
            var assertions = elements(message.document(), ASSERTION, "Assertion");
            if (assertions.isEmpty()) continue;
            inspected.add(message.evidence());
            observedAssertions += assertions.size();
            observedMultipleAssertions |= assertions.size() > 1;
            var issuers = assertions.stream()
                    .map(assertion -> directText(assertion, ASSERTION, "Issuer"))
                    .distinct().toList();
            if (issuers.size() > 1) {
                return Optional.of(outcome(
                        Outcome.VIOLATED, "browser.normal-flow.assertion-issuers-differ",
                        inspected, Map.of("assertions", observedAssertions, "issuers", issuers)));
            }
        }
        if (!observedMultipleAssertions) return Optional.empty();
        return Optional.of(outcome(
                Outcome.SATISFIED, "browser.normal-flow.assertion-issuers-consistent", inspected,
                Map.of(
                        "assertions", observedAssertions,
                        "multiple_assertions_observed", true)));
    }

    private static Optional<CaseOutcome> noBearerNotBefore(List<Parsed> messages) {
        var evidence = new ArrayList<EvidenceRef>();
        var observed = 0;
        for (var message : responses(messages)) {
            for (var confirmation : elements(message.document(), ASSERTION, "SubjectConfirmation")) {
                if (!BEARER.equals(confirmation.getAttribute("Method"))) continue;
                var data = direct(confirmation, ASSERTION, "SubjectConfirmationData");
                if (data == null) continue;
                observed++;
                evidence.add(message.evidence());
                if (data.hasAttribute("NotBefore")) {
                    return Optional.of(outcome(
                            Outcome.VIOLATED, "browser.normal-flow.bearer-not-before-present",
                            evidence, Map.of("bearer_confirmations", observed)));
                }
            }
        }
        return observed == 0 ? Optional.empty() : Optional.of(outcome(
                Outcome.SATISFIED, "browser.normal-flow.bearer-not-before-absent",
                evidence, Map.of("bearer_confirmations", observed)));
    }

    private static Optional<CaseOutcome> everyAssertionHasBearerConfirmation(List<Parsed> messages) {
        var evidence = new ArrayList<EvidenceRef>();
        var assertions = 0;
        for (var response : successfulResponses(messages)) {
            for (var assertion : elements(response.document(), ASSERTION, "Assertion")) {
                // An accompanying assertion without an AuthnStatement can be outside this profile.
                // The approved negative control is an authentication assertion that offers only
                // another confirmation method, such as holder-of-key.
                if (elements(assertion, ASSERTION, "AuthnStatement").isEmpty()) continue;
                assertions++;
                evidence.add(response.evidence());
                var bearer = elements(assertion, ASSERTION, "SubjectConfirmation").stream()
                        .anyMatch(value -> BEARER.equals(value.getAttribute("Method")));
                if (!bearer) {
                    return Optional.of(outcome(
                            Outcome.VIOLATED, "browser.normal-flow.assertion-without-bearer",
                            evidence, Map.of("assertions", assertions)));
                }
            }
        }
        return assertions == 0 ? Optional.empty() : Optional.of(outcome(
                Outcome.SATISFIED, "browser.normal-flow.assertions-have-bearer",
                evidence, Map.of("assertions", assertions)));
    }

    private static Optional<CaseOutcome> everyBearerAssertionHasAudience(List<Parsed> messages) {
        var requests = requestsById(messages);
        var evidence = new ArrayList<EvidenceRef>();
        var bearerAssertions = 0;
        var multipleBearerAssertionsObserved = false;
        for (var response : successfulResponses(messages)) {
            var requestId = response.document().getDocumentElement().getAttribute("InResponseTo");
            var request = requests.get(requestId);
            if (request == null) continue;
            var requester = directText(
                    request.document().getDocumentElement(), ASSERTION, "Issuer");
            if (requester.isBlank()) continue;
            var assertions = elements(response.document(), ASSERTION, "Assertion");
            var bearerInResponse = assertions.stream().filter(assertion ->
                    elements(assertion, ASSERTION, "SubjectConfirmation").stream()
                            .anyMatch(value -> BEARER.equals(value.getAttribute("Method")))).toList();
            multipleBearerAssertionsObserved |= bearerInResponse.size() > 1;
            for (var assertion : bearerInResponse) {
                var bearer = elements(assertion, ASSERTION, "SubjectConfirmation").stream()
                        .anyMatch(value -> BEARER.equals(value.getAttribute("Method")));
                if (!bearer) continue;
                bearerAssertions++;
                evidence.add(request.evidence());
                evidence.add(response.evidence());
                var audiences = elements(assertion, ASSERTION, "AudienceRestriction").stream()
                        .flatMap(value -> elements(value, ASSERTION, "Audience").stream())
                        .map(Element::getTextContent).toList();
                if (!audiences.contains(requester)) {
                    return Optional.of(outcome(
                            Outcome.VIOLATED, "browser.normal-flow.requester-audience-missing",
                            evidence, Map.of(
                                    "bearer_assertions", bearerAssertions,
                                    "requester", requester,
                                    "audiences", audiences)));
                }
            }
        }
        // G2 explicitly requires the multiple-assertion control. A single normal response can
        // expose a violation, but cannot positively prove that the target applies the audience
        // rule to every bearer assertion.
        return bearerAssertions == 0 || !multipleBearerAssertionsObserved
                ? Optional.empty() : Optional.of(outcome(
                Outcome.SATISFIED, "browser.normal-flow.requester-audience-present",
                evidence, Map.of(
                        "bearer_assertions", bearerAssertions,
                        "multiple_bearer_assertions_observed", true)));
    }

    private static Optional<CaseOutcome> correlatedResponses(
            List<Parsed> messages, boolean requireBearerConfirmation) {
        var requests = requestsById(messages);
        if (requests.isEmpty()) return Optional.empty();
        var correlated = new LinkedHashMap<String, Parsed>();
        var evidence = new ArrayList<EvidenceRef>();
        for (var response : successfulResponses(messages)) {
            var root = response.document().getDocumentElement();
            var inResponseTo = root.getAttribute("InResponseTo");
            if (inResponseTo.isBlank()) continue; // Unsolicited SSO is outside these obligations.
            evidence.add(response.evidence());
            if (!requests.containsKey(inResponseTo)) {
                return Optional.of(outcome(
                        Outcome.VIOLATED, "browser.normal-flow.response-correlation-unknown",
                        evidence, Map.of("in_response_to", inResponseTo)));
            }
            evidence.add(requests.get(inResponseTo).evidence());
            if (requireBearerConfirmation) {
                var confirmations = elements(response.document(), ASSERTION, "SubjectConfirmation").stream()
                        .filter(value -> BEARER.equals(value.getAttribute("Method"))).toList();
                if (confirmations.isEmpty()) continue;
                var mismatched = confirmations.stream()
                        .map(value -> direct(value, ASSERTION, "SubjectConfirmationData"))
                        .anyMatch(value -> value == null
                                || !inResponseTo.equals(value.getAttribute("InResponseTo")));
                if (mismatched) {
                    return Optional.of(outcome(
                            Outcome.VIOLATED, "browser.normal-flow.bearer-correlation-mismatch",
                            evidence, Map.of("in_response_to", inResponseTo)));
                }
            }
            correlated.put(inResponseTo, response);
        }
        // The approved controls require two consecutive flows so a target that reuses the prior
        // request ID cannot pass a one-message oracle.
        if (correlated.size() < 2) return Optional.empty();
        return Optional.of(outcome(
                Outcome.SATISFIED,
                requireBearerConfirmation
                        ? "browser.normal-flow.bearer-correlations-match"
                        : "browser.normal-flow.response-correlations-match",
                evidence.stream().distinct().toList(),
                Map.of("correlated_responses", correlated.size())));
    }

    private static Optional<CaseOutcome> noRedirectResponse(List<Parsed> messages) {
        var responses = responses(messages);
        if (responses.isEmpty()) return Optional.empty();
        var redirect = responses.stream().filter(value -> "GET".equalsIgnoreCase(value.message().method())).toList();
        if (redirect.isEmpty()) {
            // A normal POST response proves only the first observed path. The approved case also
            // requires a peer metadata fixture that advertises a Redirect ACS; without that
            // control, a positive conclusion would turn two required variants into one.
            return Optional.empty();
        }
        return Optional.of(outcome(
                Outcome.VIOLATED, "browser.normal-flow.redirect-response",
                evidence(redirect), Map.of("responses", responses.size(), "redirect_responses", redirect.size())));
    }

    private static Optional<CaseOutcome> exchangesUseTls(List<Parsed> messages) {
        var requests = messages.stream().filter(value -> isRoot(value.document(), PROTOCOL, "AuthnRequest")).toList();
        var responses = responses(messages);
        if (requests.isEmpty() || responses.isEmpty()) return Optional.empty();
        var exchanges = new ArrayList<Parsed>();
        exchanges.addAll(requests);
        exchanges.addAll(responses);
        var insecure = exchanges.stream().filter(value -> !"https".equalsIgnoreCase(
                URI.create(value.message().url()).getScheme())).toList();
        return Optional.of(outcome(
                insecure.isEmpty() ? Outcome.SATISFIED : Outcome.VIOLATED,
                insecure.isEmpty() ? "browser.normal-flow.tls" : "browser.normal-flow.cleartext-http",
                evidence(exchanges), Map.of("exchanges", exchanges.size(), "insecure_exchanges", insecure.size())));
    }

    private static Optional<CaseOutcome> responseVersionNotHigher(List<Parsed> messages) {
        var requestVersions = new LinkedHashMap<String, Version>();
        for (var message : messages) {
            if (!isRoot(message.document(), PROTOCOL, "AuthnRequest")) continue;
            var root = message.document().getDocumentElement();
            var version = Version.parse(root.getAttribute("Version"));
            if (version != null && !root.getAttribute("ID").isBlank()) {
                requestVersions.put(root.getAttribute("ID"), version);
            }
        }
        var evidence = new ArrayList<EvidenceRef>();
        var compared = 0;
        for (var response : responses(messages)) {
            var root = response.document().getDocumentElement();
            var request = requestVersions.get(root.getAttribute("InResponseTo"));
            var version = Version.parse(root.getAttribute("Version"));
            if (request == null || version == null) continue;
            compared++;
            evidence.add(response.evidence());
            if (version.compareTo(request) > 0) {
                return Optional.of(outcome(
                        Outcome.VIOLATED, "browser.normal-flow.response-version-higher",
                        evidence, Map.of("compared_responses", compared)));
            }
        }
        return compared == 0 ? Optional.empty() : Optional.of(outcome(
                Outcome.SATISFIED, "browser.normal-flow.response-version-not-higher",
                evidence, Map.of("compared_responses", compared)));
    }

    private static Optional<CaseOutcome> successfulPostResponse(List<Parsed> messages) {
        var successful = responses(messages).stream()
                .filter(value -> SUCCESS.equals(firstAttribute(value.document(), PROTOCOL, "StatusCode", "Value")))
                .toList();
        if (successful.isEmpty()) return Optional.empty();
        var post = successful.stream().filter(value -> "POST".equalsIgnoreCase(value.message().method())).toList();
        if (post.isEmpty()) return Optional.empty();
        return Optional.of(outcome(
                Outcome.SATISFIED, "browser.normal-flow.post-response-supported",
                evidence(post), Map.of("successful_post_responses", post.size())));
    }

    private static Optional<CaseOutcome> nameIdLength(
            List<Parsed> messages, String format, String label) {
        var evidence = new ArrayList<EvidenceRef>();
        var observed = 0;
        var longest = 0;
        for (var response : responses(messages)) {
            for (var nameId : elements(response.document(), ASSERTION, "NameID")) {
                if (!format.equals(nameId.getAttribute("Format"))) continue;
                observed++;
                evidence.add(response.evidence());
                var length = nameId.getTextContent().codePointCount(0, nameId.getTextContent().length());
                longest = Math.max(longest, length);
                if (length > 256) {
                    return Optional.of(outcome(
                            Outcome.VIOLATED, "browser.normal-flow." + label + "-nameid-too-long",
                            evidence, Map.of("observed_nameids", observed, "longest_code_points", longest)));
                }
            }
        }
        return observed == 0 ? Optional.empty() : Optional.of(outcome(
                Outcome.SATISFIED, "browser.normal-flow." + label + "-nameid-length",
                evidence, Map.of("observed_nameids", observed, "longest_code_points", longest)));
    }

    private static Optional<CaseOutcome> encryptionChoice(List<Parsed> messages) {
        var responses = responses(messages);
        if (responses.isEmpty()) return Optional.empty();
        if (responses.stream().anyMatch(value ->
                value.document().getElementsByTagNameNS(ASSERTION, "EncryptedAssertion").getLength() > 0)) {
            // The choice inside an encrypted assertion is not visible without a decryption observation.
            return Optional.empty();
        }
        var encryptedIds = responses.stream()
                .mapToInt(value -> value.document().getElementsByTagNameNS(ASSERTION, "EncryptedID").getLength()).sum();
        var encryptedAttributes = responses.stream()
                .mapToInt(value -> value.document().getElementsByTagNameNS(ASSERTION, "EncryptedAttribute").getLength()).sum();
        return Optional.of(outcome(
                Outcome.SATISFIED_WITH_NOTE, "browser.normal-flow.encryption-choice-recorded",
                evidence(responses), Map.of(
                        "encrypted_ids", encryptedIds,
                        "encrypted_attributes", encryptedAttributes)));
    }

    private static List<Parsed> parse(List<Message> messages) {
        var result = new ArrayList<Parsed>();
        for (var message : messages == null ? List.<Message>of() : messages) {
            try {
                result.add(new Parsed(message, SecureXml.parse(message.xml()),
                        new EvidenceRef("transcript", message.evidenceRef())));
            } catch (SamlException ignored) {
                // A normal-flow oracle cannot safely conclude from malformed evidence; manual review remains available.
            }
        }
        return List.copyOf(result);
    }

    private static List<Parsed> responses(List<Parsed> messages) {
        return messages.stream().filter(value -> isRoot(value.document(), PROTOCOL, "Response")).toList();
    }

    private static List<Parsed> successfulResponses(List<Parsed> messages) {
        return responses(messages).stream()
                .filter(value -> SUCCESS.equals(firstAttribute(
                        value.document(), PROTOCOL, "StatusCode", "Value")))
                .toList();
    }

    private static Map<String, Parsed> requestsById(List<Parsed> messages) {
        var result = new LinkedHashMap<String, Parsed>();
        for (var message : messages) {
            if (!isRoot(message.document(), PROTOCOL, "AuthnRequest")) continue;
            var root = message.document().getDocumentElement();
            var id = root.getAttribute("ID");
            if (!id.isBlank()) result.put(id, message);
        }
        return Map.copyOf(result);
    }

    private static boolean isRoot(Document document, String namespace, String localName) {
        var root = document.getDocumentElement();
        return namespace.equals(root.getNamespaceURI()) && localName.equals(root.getLocalName());
    }

    private static List<Element> elements(Document document, String namespace, String localName) {
        var nodes = document.getElementsByTagNameNS(namespace, localName);
        var result = new ArrayList<Element>();
        for (var index = 0; index < nodes.getLength(); index++) result.add((Element) nodes.item(index));
        return result;
    }

    private static List<Element> elements(Element parent, String namespace, String localName) {
        var nodes = parent.getElementsByTagNameNS(namespace, localName);
        var result = new ArrayList<Element>();
        for (var index = 0; index < nodes.getLength(); index++) result.add((Element) nodes.item(index));
        return result;
    }

    private static Element direct(Element parent, String namespace, String localName) {
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                    && namespace.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) return element;
        }
        return null;
    }

    private static String directText(Element parent, String namespace, String localName) {
        var value = direct(parent, namespace, localName);
        return value == null ? "" : value.getTextContent();
    }

    private static String firstAttribute(Document document, String namespace, String localName, String attribute) {
        var nodes = document.getElementsByTagNameNS(namespace, localName);
        return nodes.getLength() == 0 ? "" : ((Element) nodes.item(0)).getAttribute(attribute);
    }

    private static List<EvidenceRef> evidence(List<Parsed> messages) {
        return messages.stream().map(Parsed::evidence).distinct().toList();
    }

    private static CaseOutcome outcome(
            Outcome outcome, String reasonCode, List<EvidenceRef> evidence, Map<String, Object> details) {
        return new CaseOutcome(outcome, null, reasonCode, reasonCode, evidence, details);
    }

    record Message(String evidenceRef, String method, String url, byte[] xml) {
        Message {
            if (evidenceRef == null || evidenceRef.isBlank()) throw new IllegalArgumentException("evidenceRef is required");
            if (method == null || method.isBlank()) throw new IllegalArgumentException("method is required");
            URI.create(url);
            if (xml == null || xml.length == 0) throw new IllegalArgumentException("xml is required");
            xml = xml.clone();
        }
        @Override public byte[] xml() { return xml.clone(); }
    }

    private record Parsed(Message message, Document document, EvidenceRef evidence) {}

    private record Version(int major, int minor) implements Comparable<Version> {
        static Version parse(String value) {
            if (value == null || !value.matches("[0-9]+\\.[0-9]+")) return null;
            var parts = value.split("\\.");
            try { return new Version(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])); }
            catch (NumberFormatException invalid) { return null; }
        }
        @Override public int compareTo(Version other) {
            var majorValue = Integer.compare(major, other.major);
            return majorValue == 0 ? Integer.compare(minor, other.minor) : majorValue;
        }
    }
}
