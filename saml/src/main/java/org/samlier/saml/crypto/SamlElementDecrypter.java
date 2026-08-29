package org.samlier.saml.crypto;

import java.security.PrivateKey;
import org.w3c.dom.Element;

@FunctionalInterface
public interface SamlElementDecrypter {
    Element decrypt(Element encryptedWrapper, PrivateKey privateKey);
}
