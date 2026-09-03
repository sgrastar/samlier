package com.samlscope.saml.crypto;

import java.security.PrivateKey;
import org.apache.xml.security.Init;
import org.apache.xml.security.encryption.XMLCipher;
import com.samlscope.saml.normal.SamlException;
import com.samlscope.saml.normal.SecureXml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Decrypts a SAML encrypted wrapper in an isolated DOM using an in-memory key. */
public final class SamlXmlDecrypter implements SamlElementDecrypter {
    static { Init.init(); }

    @Override public Element decrypt(Element encryptedWrapper, PrivateKey privateKey) {
        if (encryptedWrapper == null) throw new IllegalArgumentException("encryptedWrapper is required");
        if (privateKey == null) throw new IllegalArgumentException("privateKey is required");
        try {
            var isolated = isolate(encryptedWrapper);
            var encryptedData = firstElement(isolated.getDocumentElement(),
                    "http://www.w3.org/2001/04/xmlenc#", "EncryptedData");
            if (encryptedData == null) throw new SamlException("Encrypted wrapper has no EncryptedData");
            var cipher = XMLCipher.getInstance();
            cipher.setSecureValidation(true);
            cipher.init(XMLCipher.DECRYPT_MODE, null);
            cipher.setKEK(privateKey);
            cipher.doFinal(isolated, encryptedData);
            var plaintext = firstElementChild(isolated.getDocumentElement());
            if (plaintext == null) throw new SamlException("Decryption produced no element");
            return plaintext;
        } catch (SamlException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new SamlException("Could not decrypt SAML encrypted element", failure);
        }
    }

    private Document isolate(Element wrapper) {
        var document = SecureXml.newDocument();
        document.appendChild(document.importNode(wrapper, true));
        return SecureXml.parse(SecureXml.serialize(document));
    }

    private Element firstElement(Element root, String namespace, String localName) {
        var nodes = root.getElementsByTagNameNS(namespace, localName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private Element firstElementChild(Element parent) {
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) return (Element) child;
        }
        return null;
    }
}
