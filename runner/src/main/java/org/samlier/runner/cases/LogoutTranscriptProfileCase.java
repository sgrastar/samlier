package org.samlier.runner.cases;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.transcript.Direction;
import org.samlier.core.transcript.TranscriptContentReader;
import org.samlier.core.transcript.TranscriptEntry;
import org.samlier.core.transcript.TranscriptRecorder;
import org.samlier.saml.normal.SamlSchemaValidation;
import org.samlier.saml.normal.SecureXml;
import org.samlier.saml.binding.RedirectSignatureVerifier;
import org.samlier.saml.crypto.XmlSignatureVerifier;
import org.w3c.dom.Element;

/** Passive Core/Profile checks over target-issued SLO messages and their Suite-side counterparts. */
public final class LogoutTranscriptProfileCase {
    public enum Rule {
        UNIQUE_IDS, IN_RESPONSE_TO, CONSENT_SIGNATURE, TOP_LEVEL_STATUS,
        RESPONSE_VERSION_CEILING, RESPONSE_VERSION_FLOOR, REQUEST_VERSION_SUPPORTED,
        REQUEST_VERSION_2, SCHEMA_STRUCTURE, ASYNC_PLACEMENT, ASYNC_CHOICE,
        RESPONSE_ISSUER_COUNT, RESPONSE_ISSUER_VALUE, RESPONSE_ISSUER_FORMAT, RESPONSE_SIGNATURE,
        REQUEST_ISSUER_COUNT, REQUEST_ISSUER_VALUE, REQUEST_ISSUER_FORMAT, REQUEST_SIGNATURE,
        REQUEST_NOT_ON_OR_AFTER, REQUEST_IDENTIFIER_MATCH, REQUEST_NOT_ON_OR_AFTER_BOUND,
        REDIRECT_LOGOUT_REQUEST_ACCEPTED
    }

    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String ASYNC = "urn:oasis:names:tc:SAML:2.0:protocol:ext:async-slo";
    private static final String STATUS_SUCCESS = "urn:oasis:names:tc:SAML:2.0:status:Success";
    private static final Set<String> TOP_STATUS = Set.of(
            STATUS_SUCCESS,
            "urn:oasis:names:tc:SAML:2.0:status:Requester",
            "urn:oasis:names:tc:SAML:2.0:status:Responder",
            "urn:oasis:names:tc:SAML:2.0:status:VersionMismatch");
    private final Rule rule;
    private final List<X509Certificate> verificationKeys;
    private final String expectedTargetEntityId;
    private final XmlSignatureVerifier xmlSignatures = new XmlSignatureVerifier();
    private final RedirectSignatureVerifier redirectSignatures = new RedirectSignatureVerifier();

    public LogoutTranscriptProfileCase(Rule rule, List<X509Certificate> verificationKeys) {
        this(rule, verificationKeys, null);
    }

    public LogoutTranscriptProfileCase(
            Rule rule, List<X509Certificate> verificationKeys, String expectedTargetEntityId) {
        this.rule = java.util.Objects.requireNonNull(rule, "rule");
        this.verificationKeys = List.copyOf(verificationKeys == null ? List.of() : verificationKeys);
        this.expectedTargetEntityId = expectedTargetEntityId;
    }

    public CaseOutcome evaluate(
            String runId, TranscriptRecorder transcript, TranscriptContentReader content) {
        var all = messages(runId, transcript, content);
        var target = all.stream().filter(value -> value.entry().direction() == Direction.INBOUND).toList();
        var targetLogout = target.stream().filter(value -> value.logout() != null).toList();
        if (targetLogout.isEmpty()) return absent();
        return switch (rule) {
            case UNIQUE_IDS -> unique(targetLogout);
            case IN_RESPONSE_TO -> inResponseTo(targetLogout, all);
            case CONSENT_SIGNATURE -> consentSignature(targetLogout);
            case TOP_LEVEL_STATUS -> topLevelStatus(targetLogout);
            case RESPONSE_VERSION_CEILING -> responseVersionCeiling(targetLogout, all);
            case RESPONSE_VERSION_FLOOR -> responseVersionFloor(targetLogout, all);
            case REQUEST_VERSION_SUPPORTED -> requestVersionSupported(targetLogout, all);
            case REQUEST_VERSION_2 -> requestVersion2(targetLogout);
            case SCHEMA_STRUCTURE -> schema(targetLogout);
            case ASYNC_PLACEMENT -> asyncPlacement(target);
            case ASYNC_CHOICE -> informationalAsync(targetLogout);
            case RESPONSE_ISSUER_COUNT -> issuerCount(targetLogout, "LogoutResponse");
            case RESPONSE_ISSUER_VALUE -> issuerValue(targetLogout, "LogoutResponse");
            case RESPONSE_ISSUER_FORMAT -> issuerFormat(targetLogout, "LogoutResponse");
            case RESPONSE_SIGNATURE -> targetSignature(targetLogout, "LogoutResponse");
            case REQUEST_ISSUER_COUNT -> issuerCount(targetLogout, "LogoutRequest");
            case REQUEST_ISSUER_VALUE -> issuerValue(targetLogout, "LogoutRequest");
            case REQUEST_ISSUER_FORMAT -> issuerFormat(targetLogout, "LogoutRequest");
            case REQUEST_SIGNATURE -> targetSignature(targetLogout, "LogoutRequest");
            case REQUEST_NOT_ON_OR_AFTER -> requestNotOnOrAfter(targetLogout);
            case REQUEST_IDENTIFIER_MATCH -> requestIdentifierMatches(targetLogout, all);
            case REQUEST_NOT_ON_OR_AFTER_BOUND -> requestNotOnOrAfterBound(targetLogout, all);
            case REDIRECT_LOGOUT_REQUEST_ACCEPTED -> redirectLogoutRequestAccepted(targetLogout, all);
        };
    }

    private CaseOutcome requestIdentifierMatches(List<Message> targetLogout, List<Message> all) {
        var issued = issuedNameIds(all);
        if (issued.isEmpty()) return CaseOutcome.notVerified(
                "issued_session_identifier_unavailable", "slo.identifier.assertion-unavailable");
        var requests = targetLogout.stream().filter(value -> is(value.logout(), "LogoutRequest")).toList();
        if (requests.isEmpty()) return optionalNotObserved("slo.LogoutRequest.not-observed");
        var matched = new ArrayList<Message>();
        for (var request : requests) {
            var nameId = direct(request.logout(), ASSERTION, "NameID");
            if (nameId == null) continue; // EncryptedID/BaseID needs a dedicated semantic/decryption oracle.
            var key = identifierKey(nameId);
            if (issued.containsKey(key)) matched.add(request);
        }
        if (matched.isEmpty()) return CaseOutcome.notVerified(
                "logout_identifier_strong_match_unobservable", "slo.identifier.strong-match-unobservable");
        return outcome(matched, List.of(), "slo.logout-request.identifier-strong-match");
    }

    private CaseOutcome requestNotOnOrAfterBound(List<Message> targetLogout, List<Message> all) {
        var issued = issuedNameIds(all);
        if (issued.isEmpty()) return CaseOutcome.notVerified(
                "issued_session_expiry_unavailable", "slo.not-on-or-after.assertion-unavailable");
        var inspected = new ArrayList<Message>();
        var violations = new ArrayList<String>();
        for (var request : targetLogout.stream().filter(value -> is(value.logout(), "LogoutRequest")).toList()) {
            var nameId = direct(request.logout(), ASSERTION, "NameID");
            if (nameId == null) continue;
            var expiry = issued.get(identifierKey(nameId));
            if (expiry == null) continue;
            try {
                var requestExpiry = java.time.Instant.parse(request.logout().getAttribute("NotOnOrAfter"));
                inspected.add(request);
                if (requestExpiry.isBefore(expiry)) violations.add(request.reference());
            } catch (java.time.format.DateTimeParseException invalid) {
                inspected.add(request);
                violations.add(request.reference());
            }
        }
        if (inspected.isEmpty()) return CaseOutcome.notVerified(
                "logout_session_expiry_correlation_unavailable", "slo.not-on-or-after.correlation-unavailable");
        return outcome(inspected, violations, "slo.logout-request.not-on-or-after-bound");
    }

    private CaseOutcome redirectLogoutRequestAccepted(List<Message> targetLogout, List<Message> all) {
        var redirectRequests = all.stream()
                .filter(value -> value.entry().direction() == Direction.OUTBOUND)
                .filter(value -> is(value.logout(), "LogoutRequest"))
                .filter(value -> "GET".equalsIgnoreCase(value.entry().method()))
                .toList();
        if (redirectRequests.isEmpty()) return optionalNotObserved("slo.redirect.logout-request.not-observed");
        var ids = redirectRequests.stream().map(value -> value.logout().getAttribute("ID")).collect(
                java.util.stream.Collectors.toSet());
        var responses = targetLogout.stream()
                .filter(value -> is(value.logout(), "LogoutResponse"))
                .filter(value -> ids.contains(value.logout().getAttribute("InResponseTo")))
                .toList();
        if (responses.isEmpty()) return CaseOutcome.notVerified(
                "redirect_logout_response_unavailable", "slo.redirect.logout-response-unavailable");
        var violations = responses.stream()
                .filter(value -> !STATUS_SUCCESS.equals(topStatus(value.logout())))
                .map(Message::reference).toList();
        return outcome(responses, violations, "slo.redirect.logout-request-accepted");
    }

    private Map<String, java.time.Instant> issuedNameIds(List<Message> all) {
        var result = new LinkedHashMap<String, java.time.Instant>();
        for (var message : all) {
            if (message.entry().direction() != Direction.INBOUND || message.logout() != null) continue;
            for (var assertion : elements(message.document().getDocumentElement(), ASSERTION, "Assertion")) {
                var subject = direct(assertion, ASSERTION, "Subject");
                var nameId = direct(subject, ASSERTION, "NameID");
                if (nameId == null) continue;
                java.time.Instant expiry = null;
                var conditions = direct(assertion, ASSERTION, "Conditions");
                if (conditions != null && !conditions.getAttribute("NotOnOrAfter").isBlank()) {
                    try { expiry = java.time.Instant.parse(conditions.getAttribute("NotOnOrAfter")); }
                    catch (java.time.format.DateTimeParseException ignored) { }
                }
                var current = result.get(identifierKey(nameId));
                if (expiry != null && (current == null || expiry.isAfter(current))) {
                    result.put(identifierKey(nameId), expiry);
                } else if (!result.containsKey(identifierKey(nameId))) {
                    result.put(identifierKey(nameId), null);
                }
            }
        }
        return result;
    }

    private String identifierKey(Element nameId) {
        return String.join("\u0000", nameId.getAttribute("Format"), nameId.getAttribute("NameQualifier"),
                nameId.getAttribute("SPNameQualifier"), nameId.getTextContent());
    }

    private List<Element> elements(Element parent, String namespace, String localName) {
        var result = new ArrayList<Element>();
        if (parent == null) return result;
        var nodes = parent.getElementsByTagNameNS(namespace, localName);
        for (var index = 0; index < nodes.getLength(); index++) result.add((Element) nodes.item(index));
        return result;
    }

    private CaseOutcome issuerCount(List<Message> messages, String type) {
        var scoped = messages.stream().filter(value -> is(value.logout(), type)).toList();
        if (scoped.isEmpty()) return optionalNotObserved("slo." + type + ".not-observed");
        var violations = scoped.stream().filter(value ->
                directElements(value.logout(), ASSERTION, "Issuer").size() != 1)
                .map(Message::reference).toList();
        return outcome(scoped, violations, "slo." + type + ".issuer-count");
    }

    private CaseOutcome issuerValue(List<Message> messages, String type) {
        if (expectedTargetEntityId == null || expectedTargetEntityId.isBlank()) {
            return CaseOutcome.notVerified(
                    "target_entity_id_unavailable", "slo.issuer.target-entity-id-unavailable");
        }
        var scoped = messages.stream().filter(value -> is(value.logout(), type)).toList();
        if (scoped.isEmpty()) return optionalNotObserved("slo." + type + ".not-observed");
        var violations = scoped.stream().filter(value -> {
            var issuers = directElements(value.logout(), ASSERTION, "Issuer");
            return issuers.size() != 1 || !expectedTargetEntityId.equals(issuers.getFirst().getTextContent());
        }).map(Message::reference).toList();
        return outcome(scoped, violations, "slo." + type + ".issuer-value");
    }

    private CaseOutcome issuerFormat(List<Message> messages, String type) {
        var scoped = messages.stream().filter(value -> is(value.logout(), type)).toList();
        if (scoped.isEmpty()) return optionalNotObserved("slo." + type + ".not-observed");
        var entity = "urn:oasis:names:tc:SAML:2.0:nameid-format:entity";
        var violations = scoped.stream().filter(value -> directElements(value.logout(), ASSERTION, "Issuer").stream()
                .anyMatch(issuer -> !issuer.getAttribute("Format").isBlank()
                        && !entity.equals(issuer.getAttribute("Format"))))
                .map(Message::reference).toList();
        return outcome(scoped, violations, "slo." + type + ".issuer-format");
    }

    private CaseOutcome targetSignature(List<Message> messages, String type) {
        var scoped = messages.stream().filter(value -> is(value.logout(), type)).toList();
        if (scoped.isEmpty()) return optionalNotObserved("slo." + type + ".not-observed");
        var violations = new ArrayList<String>();
        var unverifiable = new ArrayList<String>();
        for (var message : scoped) {
            var xml = direct(message.logout(), DS, "Signature") != null;
            var redirect = message.entry().rawQuery() != null
                    && message.entry().rawQuery().matches("(^|.*&)Signature=[^&]+(&.*|$)");
            if (!xml && !redirect) {
                violations.add(message.reference());
                continue;
            }
            if (verificationKeys.isEmpty()) {
                unverifiable.add(message.reference());
                continue;
            }
            var valid = verificationKeys.stream().anyMatch(certificate ->
                    xml && xmlSignatures.hasValidEnvelopedSignature(message.logout(), certificate)
                            || redirect && redirectSignatures.isValid(message.entry().rawQuery(), certificate));
            if (!valid) violations.add(message.reference());
        }
        if (violations.isEmpty() && !unverifiable.isEmpty()) return new CaseOutcome(
                Outcome.NOT_VERIFIED, "target_signing_key_unavailable",
                "slo.signature.key-unavailable", "slo.signature.key-unavailable",
                evidence(scoped), Map.of("unverifiable", List.copyOf(unverifiable)));
        return outcome(scoped, violations, "slo." + type + ".signature");
    }

    private CaseOutcome requestNotOnOrAfter(List<Message> messages) {
        var requests = messages.stream().filter(value -> is(value.logout(), "LogoutRequest")).toList();
        if (requests.isEmpty()) return optionalNotObserved("slo.LogoutRequest.not-observed");
        var violations = new ArrayList<String>();
        for (var request : requests) {
            var lexical = request.logout().getAttribute("NotOnOrAfter");
            try {
                if (!lexical.endsWith("Z")) throw new java.time.format.DateTimeParseException("not UTC", lexical, 0);
                java.time.Instant.parse(lexical);
            } catch (java.time.format.DateTimeParseException invalid) {
                violations.add(request.reference());
            }
        }
        return outcome(requests, violations, "slo.logout-request.not-on-or-after");
    }

    private CaseOutcome unique(List<Message> messages) {
        var seen = new HashMap<String, String>();
        var violations = new ArrayList<String>();
        for (var message : messages) {
            var id = message.logout().getAttribute("ID");
            if (id.isBlank()) continue;
            var previous = seen.putIfAbsent(id, message.digest());
            if (previous != null && !previous.equals(message.digest())) violations.add(id);
        }
        return outcome(messages, violations, "slo.id-uniqueness");
    }

    private CaseOutcome inResponseTo(List<Message> target, List<Message> all) {
        var requests = new LinkedHashMap<String, Element>();
        all.stream().filter(value -> value.entry().direction() == Direction.OUTBOUND)
                .filter(value -> is(value.logout(), "LogoutRequest"))
                .forEach(value -> requests.put(value.logout().getAttribute("ID"), value.logout()));
        var responses = target.stream().filter(value -> is(value.logout(), "LogoutResponse")).toList();
        if (responses.isEmpty()) return optionalNotObserved("slo.logout-response.not-observed");
        var violations = new ArrayList<String>();
        for (var response : responses) {
            var value = response.logout().getAttribute("InResponseTo");
            if (value.isBlank() || !requests.containsKey(value)) violations.add(response.reference());
        }
        return outcome(responses, violations, "slo.in-response-to");
    }

    private CaseOutcome consentSignature(List<Message> messages) {
        var scoped = messages.stream().filter(value -> !value.logout().getAttribute("Consent").isBlank()).toList();
        if (scoped.isEmpty()) return optionalNotObserved("slo.consent.not-observed");
        var violations = new ArrayList<String>();
        var unverifiable = new ArrayList<String>();
        for (var message : scoped) {
            var xmlSignature = message.logout().getElementsByTagNameNS(DS, "Signature").getLength() > 0;
            var bindingSignature = message.entry().rawQuery() != null
                    && message.entry().rawQuery().matches("(^|.*&)Signature=[^&]+(&.*|$)");
            if (!xmlSignature && !bindingSignature) {
                violations.add(message.reference());
                continue;
            }
            if (verificationKeys.isEmpty()) {
                unverifiable.add(message.reference());
                continue;
            }
            var xmlValid = xmlSignature && verificationKeys.stream().anyMatch(
                    certificate -> xmlSignatures.hasValidEnvelopedSignature(message.logout(), certificate));
            var redirectValid = bindingSignature && verificationKeys.stream().anyMatch(
                    certificate -> redirectSignatures.isValid(message.entry().rawQuery(), certificate));
            if (!xmlValid && !redirectValid) violations.add(message.reference());
        }
        if (violations.isEmpty() && !unverifiable.isEmpty()) return new CaseOutcome(
                Outcome.NOT_VERIFIED, "target_signing_key_unavailable",
                "slo.consent-signature.key-unavailable", "slo.consent-signature.key-unavailable",
                evidence(scoped), Map.of("unverifiable", List.copyOf(unverifiable)));
        return outcome(scoped, violations, "slo.consent-signature");
    }

    private CaseOutcome topLevelStatus(List<Message> messages) {
        var responses = messages.stream().filter(value -> is(value.logout(), "LogoutResponse")).toList();
        if (responses.isEmpty()) return optionalNotObserved("slo.logout-response.not-observed");
        var violations = new ArrayList<String>();
        for (var response : responses) {
            var status = direct(direct(response.logout(), PROTOCOL, "Status"), PROTOCOL, "StatusCode");
            if (status == null || !TOP_STATUS.contains(status.getAttribute("Value"))) {
                violations.add(response.reference());
            }
        }
        return outcome(responses, violations, "slo.top-level-status");
    }

    private CaseOutcome responseVersionCeiling(List<Message> target, List<Message> all) {
        return compareResponseVersions(target, all, true);
    }

    private CaseOutcome responseVersionFloor(List<Message> target, List<Message> all) {
        return compareResponseVersions(target, all, false);
    }

    private CaseOutcome compareResponseVersions(List<Message> target, List<Message> all, boolean ceiling) {
        var requestVersions = requestVersions(all, Direction.OUTBOUND);
        var responses = target.stream().filter(value -> is(value.logout(), "LogoutResponse")).toList();
        if (responses.isEmpty()) return optionalNotObserved("slo.logout-response.not-observed");
        var inspected = new ArrayList<Message>();
        var violations = new ArrayList<String>();
        for (var response : responses) {
            var requestVersion = requestVersions.get(response.logout().getAttribute("InResponseTo"));
            if (requestVersion == null) continue;
            inspected.add(response);
            var responseVersion = response.logout().getAttribute("Version");
            var comparison = compareVersion(responseVersion, requestVersion);
            if (ceiling && comparison > 0) violations.add(response.reference());
            if (!ceiling && major(responseVersion) < major(requestVersion)
                    && !secondaryStatus(response.logout()).endsWith(":RequestVersionTooHigh")) {
                violations.add(response.reference());
            }
        }
        if (inspected.isEmpty()) return CaseOutcome.notVerified(
                "corresponding_logout_request_unavailable", "slo.version.correlation-unavailable");
        return outcome(inspected, violations, ceiling ? "slo.response-version-ceiling" : "slo.response-version-floor");
    }

    private CaseOutcome requestVersionSupported(List<Message> target, List<Message> all) {
        var requests = target.stream().filter(value -> is(value.logout(), "LogoutRequest"))
                .filter(value -> value.logout().getElementsByTagNameNS(ASYNC, "Asynchronous").getLength() == 0).toList();
        if (requests.isEmpty()) return optionalNotObserved("slo.logout-request.not-observed");
        var responses = all.stream().filter(value -> value.entry().direction() == Direction.OUTBOUND)
                .filter(value -> is(value.logout(), "LogoutResponse")).toList();
        var responseByRequest = new HashMap<String, Message>();
        responses.forEach(value -> responseByRequest.put(value.logout().getAttribute("InResponseTo"), value));
        var unobserved = new ArrayList<String>();
        var violations = new ArrayList<String>();
        for (var request : requests) {
            var response = responseByRequest.get(request.logout().getAttribute("ID"));
            if (response == null) unobserved.add(request.reference());
            else if (PROTOCOL.concat(":VersionMismatch").equals(topStatus(response.logout()))) {
                violations.add(request.reference());
            }
        }
        if (!violations.isEmpty()) return outcome(requests, violations, "slo.request-version-supported");
        if (!unobserved.isEmpty()) return new CaseOutcome(
                Outcome.NOT_VERIFIED, "logout_response_consumption_not_observed",
                "slo.response-consumption.not-observed", "slo.response-consumption.not-observed",
                evidence(requests), Map.of("unobserved_requests", unobserved));
        return outcome(requests, List.of(), "slo.request-version-supported");
    }

    private CaseOutcome requestVersion2(List<Message> messages) {
        var requests = messages.stream().filter(value -> is(value.logout(), "LogoutRequest")).toList();
        if (requests.isEmpty()) return optionalNotObserved("slo.logout-request.not-observed");
        var violations = requests.stream().filter(value -> !"2.0".equals(value.logout().getAttribute("Version")))
                .map(Message::reference).toList();
        return outcome(requests, violations, "slo.request-version-2");
    }

    private CaseOutcome schema(List<Message> messages) {
        var violations = new ArrayList<String>();
        for (var message : messages) {
            var root = message.logout();
            var valid = SamlSchemaValidation.isValid(root, SamlSchemaValidation.SchemaKind.PROTOCOL)
                    && !root.getAttribute("ID").isBlank()
                    && "2.0".equals(root.getAttribute("Version"))
                    && !root.getAttribute("IssueInstant").isBlank();
            if (is(root, "LogoutRequest")) {
                valid &= root.getElementsByTagNameNS(ASSERTION, "NameID").getLength() > 0
                        || root.getElementsByTagNameNS(ASSERTION, "EncryptedID").getLength() > 0
                        || root.getElementsByTagNameNS(ASSERTION, "BaseID").getLength() > 0;
            } else {
                valid &= direct(root, PROTOCOL, "Status") != null;
            }
            if (!valid) violations.add(message.reference());
        }
        return outcome(messages, violations, "slo.schema-structure");
    }

    private CaseOutcome asyncPlacement(List<Message> targetMessages) {
        var asynchronous = new ArrayList<Message>();
        var violations = new ArrayList<String>();
        for (var message : targetMessages) {
            var all = message.document().getElementsByTagNameNS(ASYNC, "Asynchronous");
            if (all.getLength() == 0) continue;
            asynchronous.add(message);
            if (!is(message.logout(), "LogoutRequest")) {
                violations.add(message.reference());
                continue;
            }
            var extensions = direct(message.logout(), PROTOCOL, "Extensions");
            if (extensions == null || direct(extensions, ASYNC, "Asynchronous") == null) {
                violations.add(message.reference());
            }
        }
        if (asynchronous.isEmpty()) return optionalNotObserved("slo.async.not-observed");
        return outcome(asynchronous, violations, "slo.async-placement");
    }

    private CaseOutcome informationalAsync(List<Message> messages) {
        var requests = messages.stream().filter(value -> is(value.logout(), "LogoutRequest")).toList();
        return new CaseOutcome(
                Outcome.SATISFIED_WITH_NOTE, null, "slo.async.choice-recorded", "slo.async.choice-recorded",
                evidence(requests), Map.of(
                        "observed_requests", requests.size(),
                        "asynchronous_requests", requests.stream().filter(value ->
                                value.logout().getElementsByTagNameNS(ASYNC, "Asynchronous").getLength() > 0).count()));
    }

    private CaseOutcome absent() {
        return switch (rule) {
            case ASYNC_CHOICE -> informationalAsync(List.of());
            default -> optionalNotObserved("slo.target-message.not-observed");
        };
    }

    private CaseOutcome optionalNotObserved(String reasonCode) {
        return new CaseOutcome(
                Outcome.SATISFIED_WITH_NOTE, null, reasonCode, reasonCode, List.of(), Map.of("observed", 0));
    }

    private CaseOutcome outcome(List<Message> messages, List<String> violations, String code) {
        return new CaseOutcome(
                violations.isEmpty() ? Outcome.SATISFIED : Outcome.VIOLATED, null,
                violations.isEmpty() ? code + ".satisfied" : code + ".violated",
                violations.isEmpty() ? code + ".satisfied" : code + ".violated",
                evidence(messages), Map.of("observed", messages.size(), "violations", List.copyOf(violations)));
    }

    private List<EvidenceRef> evidence(List<Message> messages) {
        return messages.stream().map(value -> new EvidenceRef("transcript", value.reference())).distinct().toList();
    }

    private Map<String, String> requestVersions(List<Message> all, Direction direction) {
        var values = new HashMap<String, String>();
        all.stream().filter(value -> value.entry().direction() == direction)
                .filter(value -> is(value.logout(), "LogoutRequest"))
                .forEach(value -> values.put(value.logout().getAttribute("ID"), value.logout().getAttribute("Version")));
        return values;
    }

    private String topStatus(Element response) {
        var status = direct(direct(response, PROTOCOL, "Status"), PROTOCOL, "StatusCode");
        return status == null ? "" : status.getAttribute("Value");
    }

    private String secondaryStatus(Element response) {
        var status = direct(direct(response, PROTOCOL, "Status"), PROTOCOL, "StatusCode");
        var secondary = direct(status, PROTOCOL, "StatusCode");
        return secondary == null ? "" : secondary.getAttribute("Value");
    }

    private int compareVersion(String left, String right) {
        var l = parts(left); var r = parts(right);
        for (var index = 0; index < Math.max(l.length, r.length); index++) {
            var lv = index < l.length ? l[index] : 0;
            var rv = index < r.length ? r[index] : 0;
            if (lv != rv) return Integer.compare(lv, rv);
        }
        return 0;
    }

    private int major(String value) { var parts = parts(value); return parts.length == 0 ? -1 : parts[0]; }
    private int[] parts(String value) {
        try { return java.util.Arrays.stream(value.split("\\.")).mapToInt(Integer::parseInt).toArray(); }
        catch (RuntimeException invalid) { return new int[] {-1}; }
    }

    private List<Message> messages(String runId, TranscriptRecorder transcript, TranscriptContentReader content) {
        var result = new ArrayList<Message>();
        for (var entry : transcript.list(runId)) {
            if (entry.decodedSamlRef() == null || entry.decodedSamlBytes() == 0) continue;
            try {
                var xml = content.readDecodedSaml(entry);
                var document = SecureXml.parse(xml);
                Element logout = null;
                var root = document.getDocumentElement();
                if (PROTOCOL.equals(root.getNamespaceURI())
                        && ("LogoutRequest".equals(root.getLocalName()) || "LogoutResponse".equals(root.getLocalName()))) {
                    logout = root;
                } else {
                    var requests = document.getElementsByTagNameNS(PROTOCOL, "LogoutRequest");
                    var responses = document.getElementsByTagNameNS(PROTOCOL, "LogoutResponse");
                    if (requests.getLength() > 0) logout = (Element) requests.item(0);
                    else if (responses.getLength() > 0) logout = (Element) responses.item(0);
                }
                result.add(new Message(entry, document, logout, "transcript:" + entry.id(), sha256(xml)));
            } catch (RuntimeException unreadable) {
                // Another passive case owns malformed non-SLO messages.
            }
        }
        return List.copyOf(result);
    }

    private Element direct(Element parent, String namespace, String localName) {
        if (parent == null) return null;
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && namespace.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) return element;
        }
        return null;
    }
    private List<Element> directElements(Element parent, String namespace, String localName) {
        var result = new ArrayList<Element>();
        if (parent == null) return result;
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && namespace.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) result.add(element);
        }
        return result;
    }
    private boolean is(Element value, String localName) {
        return value != null && PROTOCOL.equals(value.getNamespaceURI()) && localName.equals(value.getLocalName());
    }
    private String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private record Message(
            TranscriptEntry entry, org.w3c.dom.Document document, Element logout, String reference, String digest) {}
}
