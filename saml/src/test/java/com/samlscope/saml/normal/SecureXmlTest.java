package com.samlscope.saml.normal;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SecureXmlTest {
    @Test
    void rejectsDocumentTypesOnTheNormalPath() {
        var xml = "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>"
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(SamlException.class, () -> SecureXml.parse(xml));
    }
}
