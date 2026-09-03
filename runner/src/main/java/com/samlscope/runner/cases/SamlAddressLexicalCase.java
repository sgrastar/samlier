package com.samlscope.runner.cases;

import java.util.ArrayList;
import java.util.List;
import com.samlscope.core.evaluation.CaseOutcome;
import org.w3c.dom.Element;

/** Passive lexical representation check for SubjectConfirmationData and SubjectLocality addresses. */
public final class SamlAddressLexicalCase {
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        return PassiveXmlCaseSupport.evaluate(messages, "saml.address.lexical", this::inspect);
    }

    private PassiveXmlCaseSupport.Inspection inspect(org.w3c.dom.Document document) {
        var observed = 0;
        var violations = new ArrayList<String>();
        for (var localName : List.of("SubjectConfirmationData", "SubjectLocality")) {
            var elements = document.getElementsByTagNameNS(ASSERTION, localName);
            for (var index = 0; index < elements.getLength(); index++) {
                var element = (Element) elements.item(index);
                if (!element.hasAttribute("Address")) continue;
                observed++;
                if (!isIpLiteral(element.getAttribute("Address"))) {
                    violations.add("{" + ASSERTION + "}" + localName + "/@Address");
                }
            }
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }

    private boolean isIpLiteral(String value) {
        return isIpv4(value) || isIpv6(value);
    }

    private boolean isIpv4(String value) {
        var groups = value.split("\\.", -1);
        if (groups.length != 4) return false;
        for (var group : groups) {
            if (group.isEmpty() || group.length() > 3) return false;
            for (var index = 0; index < group.length(); index++) {
                if (!Character.isDigit(group.charAt(index))) return false;
            }
            try {
                if (Integer.parseInt(group) > 255) return false;
            } catch (NumberFormatException invalid) {
                return false;
            }
        }
        return true;
    }

    private boolean isIpv6(String value) {
        if (!value.contains(":") || value.contains("%")) return false;
        var lastColon = value.lastIndexOf(':');
        if (lastColon >= 0 && value.substring(lastColon + 1).contains(".")) {
            var ipv4Tail = value.substring(lastColon + 1);
            if (!isIpv4(ipv4Tail)) return false;
            value = value.substring(0, lastColon + 1) + "0:0";
        }
        var compression = value.indexOf("::");
        if (compression != value.lastIndexOf("::")) return false;
        if (compression < 0) return validHextets(value) == 8;
        var left = value.substring(0, compression);
        var right = value.substring(compression + 2);
        var explicit = validHextets(left) + validHextets(right);
        return explicit >= 0 && explicit < 8;
    }

    private int validHextets(String value) {
        if (value.isEmpty()) return 0;
        var groups = value.split(":", -1);
        for (var group : groups) {
            if (group.isEmpty() || group.length() > 4) return -100;
            for (var index = 0; index < group.length(); index++) {
                var character = group.charAt(index);
                if (Character.digit(character, 16) < 0) return -100;
            }
        }
        return groups.length;
    }
}
