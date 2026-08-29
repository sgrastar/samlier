package org.samlier.runner.cases;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** Checks duplicate identifier declarations without relying on a validating parser. */
public final class SamlIdentifierDeclarationCase {
    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        messages = List.copyOf(messages == null ? List.of() : messages);
        if (messages.isEmpty()) return CaseOutcome.notVerified(
                "no_target_generated_saml_messages", "transcript.no-target-generated-saml");
        var violations = new ArrayList<String>();
        var evidence = new ArrayList<EvidenceRef>();
        var unverifiable = new ArrayList<EvidenceRef>();
        var observed = 0;
        for (var message : messages) {
            var ref = new EvidenceRef("transcript", message.evidenceRef());
            evidence.add(ref);
            if (hasDuplicateIdAttribute(message.xml())) {
                violations.add(message.evidenceRef() + "#duplicate-ID-attribute");
                continue;
            }
            try {
                var document = SecureXml.parse(message.xml());
                var ids = new HashMap<String, String>();
                var elements = document.getElementsByTagNameNS("*", "*");
                for (var index = 0; index < elements.getLength(); index++) {
                    var element = (Element) elements.item(index);
                    if (!element.hasAttribute("ID")) continue;
                    observed++;
                    var id = element.getAttribute("ID");
                    var previous = ids.putIfAbsent(id, element.getTagName());
                    if (previous != null) violations.add(message.evidenceRef() + "#duplicate-xs-ID:" + id);
                }
            } catch (SamlException malformed) {
                unverifiable.add(ref);
            }
        }
        if (!violations.isEmpty()) return new CaseOutcome(
                Outcome.VIOLATED, null, "saml.identifier-declaration.violated",
                "case.saml.identifier-declaration.violated", evidence,
                java.util.Map.of("violations", violations, "observed_identifiers", observed));
        if (!unverifiable.isEmpty()) return new CaseOutcome(
                Outcome.NOT_VERIFIED, "target_message_unparseable", "saml.identifier-declaration.unparseable",
                "case.saml.identifier-declaration.unparseable", unverifiable,
                java.util.Map.of("unparseable_messages", unverifiable.size(), "observed_identifiers", observed));
        return new CaseOutcome(
                observed == 0 ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED,
                null, "saml.identifier-declaration.satisfied", "case.saml.identifier-declaration.satisfied",
                evidence, java.util.Map.of("observed_identifiers", observed));
    }

    private boolean hasDuplicateIdAttribute(byte[] xml) {
        var text = new String(xml, java.nio.charset.StandardCharsets.UTF_8);
        for (var cursor = 0; cursor < text.length();) {
            var start = text.indexOf('<', cursor);
            if (start < 0 || start + 1 >= text.length()) return false;
            var marker = text.charAt(start + 1);
            var end = endOfMarkup(text, start + 1);
            if (end < 0) return false;
            if (marker != '!' && marker != '?' && marker != '/') {
                var count = 0;
                var attributeCursor = skipName(text, start + 1, end);
                while (attributeCursor < end) {
                    attributeCursor = skipWhitespaceAndSlash(text, attributeCursor, end);
                    var nameStart = attributeCursor;
                    attributeCursor = skipName(text, attributeCursor, end);
                    if (nameStart == attributeCursor) break;
                    var name = text.substring(nameStart, attributeCursor);
                    attributeCursor = skipWhitespaceAndSlash(text, attributeCursor, end);
                    if (attributeCursor >= end || text.charAt(attributeCursor) != '=') break;
                    if ("ID".equals(name) && ++count > 1) return true;
                    attributeCursor++;
                    attributeCursor = skipWhitespaceAndSlash(text, attributeCursor, end);
                    if (attributeCursor >= end) break;
                    var quote = text.charAt(attributeCursor++);
                    if (quote != '\'' && quote != '"') break;
                    var valueEnd = text.indexOf(quote, attributeCursor);
                    if (valueEnd < 0 || valueEnd > end) break;
                    attributeCursor = valueEnd + 1;
                }
            }
            cursor = end + 1;
        }
        return false;
    }

    private int endOfMarkup(String text, int from) {
        char quote = 0;
        for (var index = from; index < text.length(); index++) {
            var character = text.charAt(index);
            if (quote == 0 && (character == '\'' || character == '"')) quote = character;
            else if (quote != 0 && character == quote) quote = 0;
            else if (quote == 0 && character == '>') return index;
        }
        return -1;
    }

    private int skipName(String text, int cursor, int end) {
        while (cursor < end) {
            var character = text.charAt(cursor);
            if (Character.isWhitespace(character) || character == '=' || character == '/' || character == '>') break;
            cursor++;
        }
        return cursor;
    }

    private int skipWhitespaceAndSlash(String text, int cursor, int end) {
        while (cursor < end && (Character.isWhitespace(text.charAt(cursor)) || text.charAt(cursor) == '/')) cursor++;
        return cursor;
    }
}
