package org.samlier.saml.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPairGenerator;
import javax.crypto.KeyGenerator;
import org.apache.xml.security.Init;
import org.apache.xml.security.encryption.XMLCipher;
import org.apache.xml.security.keys.KeyInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SecureXml;
import org.w3c.dom.Element;

class SamlXmlDecrypterTest {
    @BeforeAll static void initializeXmlSecurity() { Init.init(); }

    @Test
    void decryptsAnElementUsingOnlyTheRunScopedPrivateKey() throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        var pair = keys.generateKeyPair();
        var wrapper = encryptedWrapper("EncryptedAssertion", """
                <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" ID="_a"/>
                """, pair.getPublic());

        var plaintext = new SamlXmlDecrypter().decrypt(wrapper, pair.getPrivate());

        assertEquals("Assertion", plaintext.getLocalName());
        assertEquals("_a", plaintext.getAttribute("ID"));
    }

    @Test
    void wrongKeyDoesNotBecomePlausiblePlaintext() throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        var pair = keys.generateKeyPair();
        var wrong = keys.generateKeyPair();
        var wrapper = encryptedWrapper("EncryptedID", """
                <saml:NameID xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">subject</saml:NameID>
                """, pair.getPublic());

        assertThrows(SamlException.class, () -> new SamlXmlDecrypter().decrypt(wrapper, wrong.getPrivate()));
    }

    static Element encryptedWrapper(String wrapperName, String plaintextXml, java.security.PublicKey publicKey) throws Exception {
        var document = SecureXml.parse(("""
                <saml:%s xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">%s</saml:%s>
                """).formatted(wrapperName, plaintextXml, wrapperName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        org.w3c.dom.Node child = document.getDocumentElement().getFirstChild();
        while (child != null && child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
            child = child.getNextSibling();
        }
        var plaintext = (Element) child;
        var keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128);
        var dataKey = keyGenerator.generateKey();

        var dataCipher = XMLCipher.getInstance(XMLCipher.AES_128_GCM);
        dataCipher.init(XMLCipher.ENCRYPT_MODE, dataKey);
        var keyCipher = XMLCipher.getInstance(XMLCipher.RSA_OAEP);
        keyCipher.init(XMLCipher.WRAP_MODE, publicKey);
        var encryptedKey = keyCipher.encryptKey(document, dataKey);
        var keyInfo = new KeyInfo(document);
        keyInfo.add(encryptedKey);
        dataCipher.getEncryptedData().setKeyInfo(keyInfo);
        dataCipher.doFinal(document, plaintext);
        return document.getDocumentElement();
    }
}
