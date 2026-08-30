package org.samlier.runner.cases;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.XMLConstants;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SecureXml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Static publisher-side checks proven by the target's own metadata document. */
final class TargetMetadataObservation {
    private static final String MD = "urn:oasis:names:tc:SAML:2.0:metadata";
    private static final String DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String ALG = "urn:oasis:names:tc:SAML:metadata:algsupport";
    private static final String UI = "urn:oasis:names:tc:SAML:metadata:ui";
    private static final String SAML2 = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final Set<String> LOCALIZED_UI_ELEMENTS = Set.of(
            "DisplayName", "Description", "InformationURL", "PrivacyStatementURL", "Keywords");
    private static final Set<String> ROLE_ELEMENTS = Set.of(
            "RoleDescriptor", "IDPSSODescriptor", "SPSSODescriptor", "AuthnAuthorityDescriptor",
            "AttributeAuthorityDescriptor", "PDPDescriptor");
    private static final Set<String> CONCRETE_SAML_ROLE_ELEMENTS = Set.of(
            "IDPSSODescriptor", "SPSSODescriptor", "AuthnAuthorityDescriptor",
            "AttributeAuthorityDescriptor", "PDPDescriptor");
    private static final Set<String> ONE_DIRECTION_ENDPOINTS = Set.of(
            "ArtifactResolutionService", "NameIDMappingService", "SingleSignOnService");
    private static final Set<String> DATA_ENCRYPTION = Set.of(
            "http://www.w3.org/2001/04/xmlenc#aes128-cbc",
            "http://www.w3.org/2001/04/xmlenc#aes192-cbc",
            "http://www.w3.org/2001/04/xmlenc#aes256-cbc",
            "http://www.w3.org/2009/xmlenc11#aes128-gcm",
            "http://www.w3.org/2009/xmlenc11#aes192-gcm",
            "http://www.w3.org/2009/xmlenc11#aes256-gcm",
            "http://www.w3.org/2001/04/xmlenc#tripledes-cbc");
    private static final Set<String> KEY_TRANSPORT_OR_AGREEMENT = Set.of(
            "http://www.w3.org/2001/04/xmlenc#rsa-1_5",
            "http://www.w3.org/2001/04/xmlenc#rsa-oaep-mgf1p",
            "http://www.w3.org/2009/xmlenc11#rsa-oaep",
            "http://www.w3.org/2009/xmlenc11#ECDH-ES");

    private TargetMetadataObservation() {}

    static boolean supports(String caseId) {
        return suffix(caseId, "a7") || suffix(caseId, "a9") || suffix(caseId, "ab")
                || suffix(caseId, "c9") || suffix(caseId, "ca") || suffix(caseId, "ce")
                || suffix(caseId, "e1") || suffix(caseId, "e4") || suffix(caseId, "e6")
                || suffix(caseId, "ec") || suffix(caseId, "f1") || suffix(caseId, "f2")
                || suffix(caseId, "f3") || suffix(caseId, "f4") || suffix(caseId, "fk")
                || idpSuffix(caseId, "fc") || idpSuffix(caseId, "fd") || idpSuffix(caseId, "fe");
    }

    static Optional<CaseOutcome> evaluate(String caseId, byte[] metadata, Instant now) {
        if (!supports(caseId) || metadata == null || metadata.length == 0) return Optional.empty();
        final Document document;
        try { document = SecureXml.parse(metadata); }
        catch (SamlException invalid) { return Optional.empty(); }
        var evidence = List.of(new EvidenceRef("target-metadata", digest(metadata)));
        if (suffix(caseId, "a7")) return Optional.of(roleOverlap(document, evidence));
        if (suffix(caseId, "a9")) return Optional.of(saml2RoleProtocol(document, evidence));
        if (suffix(caseId, "ab")) return Optional.of(oneDirectionResponseLocation(document, evidence));
        if (suffix(caseId, "c9")) return Optional.of(keyInfoRepresentation(document, evidence));
        if (suffix(caseId, "ca")) return Optional.of(singleCertificate(document, evidence));
        if (suffix(caseId, "ce")) return certificateValidity(document, evidence, now);
        if (suffix(caseId, "e1")) return Optional.of(encryptionAlgorithmCategories(document, evidence));
        if (suffix(caseId, "e4")) return Optional.of(requiredAlgorithm(document, MD, "EncryptionMethod", evidence));
        if (suffix(caseId, "e6")) return Optional.of(publishedSignatureAlgorithms(document, evidence));
        if (suffix(caseId, "ec")) return Optional.of(requiredAlgorithmElements(document, evidence));
        if (suffix(caseId, "f1")) return Optional.of(uiInfoPlacement(document, evidence));
        if (suffix(caseId, "f2")) return Optional.of(nonEmptyUiContainer(
                document, "UIInfo", "metadata.publisher.ui-info-content", evidence));
        if (suffix(caseId, "f3")) return Optional.of(singlePerExtensions(
                document, "UIInfo", "metadata.publisher.ui-info-cardinality", evidence));
        if (suffix(caseId, "f4")) return Optional.of(localizedUiLanguages(document, evidence));
        if (idpSuffix(caseId, "fc")) return Optional.of(discoHintsPlacement(document, evidence));
        if (idpSuffix(caseId, "fd")) return Optional.of(nonEmptyUiContainer(
                document, "DiscoHints", "metadata.publisher.disco-hints-content", evidence));
        if (idpSuffix(caseId, "fe")) return Optional.of(singlePerExtensions(
                document, "DiscoHints", "metadata.publisher.disco-hints-cardinality", evidence));
        if (suffix(caseId, "fk")) return Optional.of(logoDimensions(document, evidence));
        return Optional.empty();
    }

    private static CaseOutcome roleOverlap(Document document, List<EvidenceRef> evidence) {
        var inspected = 0;
        var violations = new ArrayList<String>();
        for (var entity : elements(document, MD, "EntityDescriptor")) {
            var byType = new java.util.LinkedHashMap<String, List<Element>>();
            for (var child = entity.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element role && MD.equals(role.getNamespaceURI())
                        && ROLE_ELEMENTS.contains(role.getLocalName())) {
                    byType.computeIfAbsent(roleType(role), ignored -> new ArrayList<>()).add(role);
                }
            }
            for (var entry : byType.entrySet()) {
                if (entry.getValue().size() < 2) continue;
                inspected += entry.getValue().size();
                var seen = new HashSet<String>();
                for (var role : entry.getValue()) {
                    for (var protocol : tokens(role.getAttribute("protocolSupportEnumeration"))) {
                        if (!seen.add(protocol)) violations.add(entry.getKey() + ":" + protocol);
                    }
                }
            }
        }
        return result(
                violations.isEmpty() ? (inspected == 0 ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED)
                        : Outcome.VIOLATED,
                "metadata.publisher.role-protocol-overlap", evidence,
                Map.of("compared_roles", inspected, "overlaps", violations));
    }

    private static CaseOutcome saml2RoleProtocol(Document document, List<EvidenceRef> evidence) {
        var roles = new ArrayList<Element>();
        for (var role : CONCRETE_SAML_ROLE_ELEMENTS) roles.addAll(elements(document, MD, role));
        var violations = roles.stream()
                .filter(role -> !tokens(role.getAttribute("protocolSupportEnumeration")).contains(SAML2))
                .map(Element::getLocalName).toList();
        return result(
                violations.isEmpty() ? (roles.isEmpty() ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED)
                        : Outcome.VIOLATED,
                "metadata.publisher.saml2-role-protocol", evidence,
                Map.of("roles", roles.size(), "missing_protocol", violations));
    }

    private static CaseOutcome oneDirectionResponseLocation(Document document, List<EvidenceRef> evidence) {
        var observed = 0;
        var violations = new ArrayList<String>();
        for (var localName : ONE_DIRECTION_ENDPOINTS) {
            for (var endpoint : elements(document, MD, localName)) {
                observed++;
                if (endpoint.hasAttribute("ResponseLocation")) violations.add(localName);
            }
        }
        return result(
                violations.isEmpty() ? (observed == 0 ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED)
                        : Outcome.VIOLATED,
                "metadata.publisher.one-direction-response-location", evidence,
                Map.of("endpoints", observed, "violations", violations));
    }

    private static CaseOutcome keyInfoRepresentation(Document document, List<EvidenceRef> evidence) {
        var descriptors = elements(document, MD, "KeyDescriptor");
        var violations = new ArrayList<Integer>();
        for (var index = 0; index < descriptors.size(); index++) {
            var keyInfo = direct(descriptors.get(index), DS, "KeyInfo");
            var keyValues = keyInfo == null ? 0 : keyInfo.getElementsByTagNameNS(DS, "KeyValue").getLength();
            var certificates = keyInfo == null ? 0 : keyInfo.getElementsByTagNameNS(DS, "X509Certificate").getLength();
            if (keyValues == 0 && certificates != 1) violations.add(index);
        }
        return result(
                violations.isEmpty() ? (descriptors.isEmpty() ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED)
                        : Outcome.VIOLATED,
                "metadata.publisher.key-representation", evidence,
                Map.of("key_descriptors", descriptors.size(), "violations", violations));
    }

    private static CaseOutcome singleCertificate(Document document, List<EvidenceRef> evidence) {
        var data = keyDescriptorElements(document, DS, "X509Data");
        var violations = new ArrayList<Integer>();
        var observed = 0;
        for (var index = 0; index < data.size(); index++) {
            var count = data.get(index).getElementsByTagNameNS(DS, "X509Certificate").getLength();
            if (count == 0) continue;
            observed++;
            if (count != 1) violations.add(index);
        }
        return result(
                violations.isEmpty() ? (observed == 0 ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED)
                        : Outcome.VIOLATED,
                "metadata.publisher.single-certificate", evidence,
                Map.of("x509_representations", observed, "violations", violations));
    }

    private static Optional<CaseOutcome> certificateValidity(
            Document document, List<EvidenceRef> evidence, Instant now) {
        var certificates = keyDescriptorElements(document, DS, "X509Certificate");
        if (certificates.isEmpty()) {
            return Optional.of(result(Outcome.SATISFIED_WITH_NOTE,
                    "metadata.publisher.no-certificates", evidence, Map.of("certificates", 0)));
        }
        var invalid = new ArrayList<Integer>();
        try {
            var factory = CertificateFactory.getInstance("X.509");
            for (var index = 0; index < certificates.size(); index++) {
                var encoded = certificates.get(index).getTextContent().replaceAll("\\s+", "");
                var certificate = (X509Certificate) factory.generateCertificate(
                        new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
                if (now.isAfter(certificate.getNotAfter().toInstant())) invalid.add(index);
            }
        } catch (Exception unparseable) {
            return Optional.empty();
        }
        return Optional.of(result(
                invalid.isEmpty() ? Outcome.SATISFIED : Outcome.VIOLATED,
                "metadata.publisher.certificate-validity", evidence,
                Map.of("certificates", certificates.size(), "not_currently_valid", invalid)));
    }

    private static CaseOutcome encryptionAlgorithmCategories(Document document, List<EvidenceRef> evidence) {
        var descriptors = elements(document, MD, "KeyDescriptor").stream()
                // An omitted @use means the key may be used for both signing and encryption.
                .filter(value -> !"signing".equals(value.getAttribute("use")))
                .toList();
        var violations = new ArrayList<Integer>();
        for (var index = 0; index < descriptors.size(); index++) {
            var algorithms = elements(descriptors.get(index), MD, "EncryptionMethod").stream()
                    .map(value -> value.getAttribute("Algorithm")).collect(java.util.stream.Collectors.toSet());
            if (algorithms.stream().noneMatch(DATA_ENCRYPTION::contains)
                    || algorithms.stream().noneMatch(KEY_TRANSPORT_OR_AGREEMENT::contains)) {
                violations.add(index);
            }
        }
        return result(
                violations.isEmpty() ? (descriptors.isEmpty() ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED)
                        : Outcome.VIOLATED,
                "metadata.publisher.encryption-algorithm-categories", evidence,
                Map.of("encryption_key_descriptors", descriptors.size(), "violations", violations));
    }

    private static CaseOutcome requiredAlgorithm(
            Document document, String namespace, String localName, List<EvidenceRef> evidence) {
        var methods = elements(document, namespace, localName);
        var violations = methods.stream().filter(value -> value.getAttribute("Algorithm").isBlank()).count();
        return result(
                violations == 0 ? (methods.isEmpty() ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED)
                        : Outcome.VIOLATED,
                "metadata.publisher.required-algorithm", evidence,
                Map.of("elements", methods.size(), "missing_algorithm", violations));
    }

    private static CaseOutcome publishedSignatureAlgorithms(Document document, List<EvidenceRef> evidence) {
        var entities = elements(document, MD, "EntityDescriptor");
        var missing = new ArrayList<Integer>();
        var digests = 0;
        var signatures = 0;
        for (var index = 0; index < entities.size(); index++) {
            var entityDigests = elements(entities.get(index), ALG, "DigestMethod").size();
            var entitySignatures = elements(entities.get(index), ALG, "SigningMethod").size();
            digests += entityDigests;
            signatures += entitySignatures;
            if (entityDigests == 0 || entitySignatures == 0) missing.add(index);
        }
        return result(
                !entities.isEmpty() && missing.isEmpty() ? Outcome.SATISFIED : Outcome.VIOLATED,
                "metadata.publisher.signature-algorithm-capabilities", evidence,
                Map.of(
                        "entities", entities.size(),
                        "digest_methods", digests,
                        "signing_methods", signatures,
                        "entities_missing_capabilities", missing));
    }

    private static CaseOutcome requiredAlgorithmElements(Document document, List<EvidenceRef> evidence) {
        var elements = new ArrayList<Element>();
        elements.addAll(elements(document, ALG, "DigestMethod"));
        elements.addAll(elements(document, ALG, "SigningMethod"));
        var violations = elements.stream().filter(value -> value.getAttribute("Algorithm").isBlank()).count();
        return result(
                violations == 0 ? (elements.isEmpty() ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED)
                        : Outcome.VIOLATED,
                "metadata.publisher.algorithm-support-uri", evidence,
                Map.of("elements", elements.size(), "missing_algorithm", violations));
    }

    private static CaseOutcome uiInfoPlacement(Document document, List<EvidenceRef> evidence) {
        var uiInfo = elements(document, UI, "UIInfo");
        var invalid = new ArrayList<Integer>();
        for (var index = 0; index < uiInfo.size(); index++) {
            var extensions = parent(uiInfo.get(index));
            var role = extensions == null ? null : parent(extensions);
            if (extensions == null || !MD.equals(extensions.getNamespaceURI())
                    || !"Extensions".equals(extensions.getLocalName())
                    || role == null || !MD.equals(role.getNamespaceURI())
                    || !ROLE_ELEMENTS.contains(role.getLocalName())) {
                invalid.add(index);
            }
        }
        return conditionalResult(
                uiInfo.size(), invalid, "metadata.publisher.ui-info-placement", evidence,
                Map.of("ui_info", uiInfo.size(), "invalid_placement", invalid));
    }

    private static CaseOutcome discoHintsPlacement(Document document, List<EvidenceRef> evidence) {
        var hints = elements(document, UI, "DiscoHints");
        var invalid = new ArrayList<Integer>();
        for (var index = 0; index < hints.size(); index++) {
            var extensions = parent(hints.get(index));
            var role = extensions == null ? null : parent(extensions);
            if (extensions == null || !MD.equals(extensions.getNamespaceURI())
                    || !"Extensions".equals(extensions.getLocalName())
                    || role == null || !MD.equals(role.getNamespaceURI())
                    || !"IDPSSODescriptor".equals(role.getLocalName())) {
                invalid.add(index);
            }
        }
        return conditionalResult(
                hints.size(), invalid, "metadata.publisher.disco-hints-placement", evidence,
                Map.of("disco_hints", hints.size(), "invalid_placement", invalid));
    }

    private static CaseOutcome nonEmptyUiContainer(
            Document document, String localName, String code, List<EvidenceRef> evidence) {
        var containers = elements(document, UI, localName);
        var empty = new ArrayList<Integer>();
        for (var index = 0; index < containers.size(); index++) {
            if (directElements(containers.get(index)).isEmpty()) empty.add(index);
        }
        return conditionalResult(
                containers.size(), empty, code, evidence,
                Map.of("containers", containers.size(), "empty", empty));
    }

    private static CaseOutcome singlePerExtensions(
            Document document, String localName, String code, List<EvidenceRef> evidence) {
        var observed = elements(document, UI, localName).size();
        var invalid = new ArrayList<Integer>();
        var extensions = elements(document, MD, "Extensions");
        for (var index = 0; index < extensions.size(); index++) {
            if (directElements(extensions.get(index), UI, localName).size() > 1) invalid.add(index);
        }
        return conditionalResult(
                observed, invalid, code, evidence,
                Map.of("elements", observed, "extensions_with_duplicates", invalid));
    }

    private static CaseOutcome localizedUiLanguages(Document document, List<EvidenceRef> evidence) {
        var uiInfo = elements(document, UI, "UIInfo");
        var localized = 0;
        var duplicates = new ArrayList<String>();
        for (var index = 0; index < uiInfo.size(); index++) {
            var languages = new java.util.LinkedHashMap<String, Set<String>>();
            for (var child : directElements(uiInfo.get(index))) {
                if (!UI.equals(child.getNamespaceURI())
                        || !LOCALIZED_UI_ELEMENTS.contains(child.getLocalName())) continue;
                localized++;
                var language = child.getAttributeNS(XMLConstants.XML_NS_URI, "lang");
                var seen = languages.computeIfAbsent(child.getLocalName(), ignored -> new HashSet<>());
                if (!seen.add(language)) duplicates.add(index + ":" + child.getLocalName() + ":" + language);
            }
        }
        return conditionalResult(
                localized, duplicates, "metadata.publisher.ui-info-languages", evidence,
                Map.of("localized_elements", localized, "duplicates", duplicates));
    }

    private static CaseOutcome logoDimensions(Document document, List<EvidenceRef> evidence) {
        var logos = elements(document, UI, "Logo");
        var invalid = new ArrayList<Integer>();
        for (var index = 0; index < logos.size(); index++) {
            var logo = logos.get(index);
            if (!logo.hasAttribute("height") || !logo.hasAttribute("width")) invalid.add(index);
        }
        return conditionalResult(
                logos.size(), invalid, "metadata.publisher.logo-dimensions", evidence,
                Map.of("logos", logos.size(), "missing_dimensions", invalid));
    }

    private static CaseOutcome conditionalResult(
            int observed, List<?> violations, String code,
            List<EvidenceRef> evidence, Map<String, Object> details) {
        var outcome = !violations.isEmpty()
                ? Outcome.VIOLATED
                : observed == 0 ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED;
        return result(outcome, code, evidence, details);
    }

    private static boolean suffix(String caseId, String suffix) {
        return caseId.startsWith("IIP-MD05-" + suffix + "-")
                && (caseId.endsWith("-idp-01") || caseId.endsWith("-sp-01"));
    }

    private static boolean idpSuffix(String caseId, String suffix) {
        return caseId.equals("IIP-MD05-" + suffix + "-idp-01");
    }

    private static List<Element> roleElements(Document document) {
        var result = new ArrayList<Element>();
        for (var role : ROLE_ELEMENTS) result.addAll(elements(document, MD, role));
        return result;
    }

    private static String roleType(Element role) {
        if (!"RoleDescriptor".equals(role.getLocalName())) return role.getLocalName();
        var lexical = role.getAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type");
        if (lexical.isBlank()) return "RoleDescriptor#untyped";
        var separator = lexical.indexOf(':');
        var prefix = separator < 0 ? null : lexical.substring(0, separator);
        var localName = separator < 0 ? lexical : lexical.substring(separator + 1);
        var namespace = role.lookupNamespaceURI(prefix);
        return namespace == null ? lexical : "{" + namespace + "}" + localName;
    }

    private static List<Element> keyDescriptorElements(
            Document document, String namespace, String localName) {
        var result = new ArrayList<Element>();
        for (var descriptor : elements(document, MD, "KeyDescriptor")) {
            var keyInfo = direct(descriptor, DS, "KeyInfo");
            if (keyInfo != null) result.addAll(elements(keyInfo, namespace, localName));
        }
        return result;
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
            if (child instanceof Element element && namespace.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) return element;
        }
        return null;
    }

    private static Element parent(Element element) {
        return element.getParentNode() instanceof Element parent ? parent : null;
    }

    private static List<Element> directElements(Element parent) {
        var result = new ArrayList<Element>();
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) result.add(element);
        }
        return result;
    }

    private static List<Element> directElements(Element parent, String namespace, String localName) {
        return directElements(parent).stream()
                .filter(value -> namespace.equals(value.getNamespaceURI())
                        && localName.equals(value.getLocalName()))
                .toList();
    }

    private static Set<String> tokens(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Set.copyOf(new HashSet<>(List.of(value.trim().split("\\s+"))));
    }

    private static CaseOutcome result(
            Outcome outcome, String code, List<EvidenceRef> evidence, Map<String, Object> details) {
        return new CaseOutcome(outcome, null, code, code, evidence, details);
    }

    private static String digest(byte[] value) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
