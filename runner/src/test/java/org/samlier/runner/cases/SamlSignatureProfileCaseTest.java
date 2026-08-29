package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.samlier.core.evaluation.Outcome;

class SamlSignatureProfileCaseTest {
    @Test
    void conformingSignatureSatisfiesEveryProfileRule() {
        var message = message("good", signature(""));

        for (var rule : SamlSignatureProfileCase.Rule.values()) {
            assertEquals(Outcome.SATISFIED,
                    new SamlSignatureProfileCase(rule).evaluate(List.of(message)).outcome(), rule.name());
        }
    }

    @Test
    void noSignatureIsSatisfiedWithNoteForEveryRule() {
        var message = message("unsigned", """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol" ID="r1"/>
                """);

        for (var rule : SamlSignatureProfileCase.Rule.values()) {
            assertEquals(Outcome.SATISFIED_WITH_NOTE,
                    new SamlSignatureProfileCase(rule).evaluate(List.of(message)).outcome(), rule.name());
        }
    }

    @Test
    void detectsEachDisallowedSignatureShape() {
        assertViolated(SamlSignatureProfileCase.Rule.ENVELOPED, """
                <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#"><ds:SignedInfo/></ds:Signature>
                """);
        assertViolated(SamlSignatureProfileCase.Rule.SIGNED_ROOT_ID, signature("ID=\"\""));
        assertViolated(SamlSignatureProfileCase.Rule.SINGLE_ROOT_REFERENCE,
                signature("", "<ds:Reference URI=\"#r1\"/><ds:Reference URI=\"#r1\"/>"));
        assertViolated(SamlSignatureProfileCase.Rule.EXCLUSIVE_CANONICALIZATION,
                signature("", "<ds:Reference URI=\"#r1\"><ds:Transforms><ds:Transform Algorithm=\"http://www.w3.org/2000/09/xmldsig#enveloped-signature\"/></ds:Transforms></ds:Reference>",
                        "http://www.w3.org/TR/2001/REC-xml-c14n-20010315"));
        assertViolated(SamlSignatureProfileCase.Rule.ALLOWED_TRANSFORMS,
                signature("", "<ds:Reference URI=\"#r1\"><ds:Transforms><ds:Transform Algorithm=\"http://www.w3.org/TR/1999/REC-xpath-19991116\"/></ds:Transforms></ds:Reference>"));
        assertViolated(SamlSignatureProfileCase.Rule.NO_OBJECT,
                signature("").replace("</ds:Signature>", "<ds:Object/></ds:Signature>"));
    }

    private void assertViolated(SamlSignatureProfileCase.Rule rule, String xml) {
        assertEquals(Outcome.VIOLATED,
                new SamlSignatureProfileCase(rule).evaluate(List.of(message("bad", xml))).outcome());
    }

    private String signature(String rootAttributes) {
        return signature(rootAttributes, """
                <ds:Reference URI="#r1"><ds:Transforms>
                  <ds:Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"/>
                  <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
                </ds:Transforms></ds:Reference>
                """);
    }

    private String signature(String rootAttributes, String reference) {
        return signature(rootAttributes, reference, "http://www.w3.org/2001/10/xml-exc-c14n#");
    }

    private String signature(String rootAttributes, String reference, String canonicalization) {
        var id = rootAttributes.isBlank() ? "ID=\"r1\"" : rootAttributes;
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:ds="http://www.w3.org/2000/09/xmldsig#" %s>
                  <ds:Signature><ds:SignedInfo>
                    <ds:CanonicalizationMethod Algorithm="%s"/>
                    %s
                  </ds:SignedInfo></ds:Signature>
                </samlp:Response>
                """.formatted(id, canonicalization, reference);
    }

    private TargetTranscriptMessages.Message message(String ref, String xml) {
        return new TargetTranscriptMessages.Message(ref, xml.getBytes(StandardCharsets.UTF_8));
    }
}
