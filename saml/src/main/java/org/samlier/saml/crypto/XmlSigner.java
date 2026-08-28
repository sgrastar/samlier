package org.samlier.saml.crypto;

import org.apache.xml.security.Init;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.algorithms.MessageDigestAlgorithm;
import org.samlier.saml.normal.SamlException;
import org.w3c.dom.Element;

public final class XmlSigner {
    static { Init.init(); }

    public void sign(Element target, PlanCredentials credentials, Element insertBefore) {
        try {
            var document = target.getOwnerDocument();
            var id = target.getAttribute("ID");
            if (id.isBlank()) throw new SamlException("Signed element has no ID");
            target.setIdAttribute("ID", true);
            var signature = new XMLSignature(
                    document,
                    "",
                    XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
                    Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
            if (insertBefore == null) target.appendChild(signature.getElement());
            else target.insertBefore(signature.getElement(), insertBefore);
            var transforms = new Transforms(document);
            transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
            transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
            signature.addDocument("#" + id, transforms, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256);
            signature.addKeyInfo(credentials.certificate());
            signature.sign(credentials.privateKey());
        } catch (SamlException e) {
            throw e;
        } catch (Exception e) {
            throw new SamlException("Could not sign XML", e);
        }
    }
}
