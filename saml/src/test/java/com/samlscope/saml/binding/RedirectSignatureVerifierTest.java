package com.samlscope.saml.binding;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.samlscope.saml.crypto.FilePlanKeyStore;

class RedirectSignatureVerifierTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void verifiesTheRawEncodedQueryAndRejectsMutation() throws Exception {
        var credentials = new FilePlanKeyStore(
                directory, Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC))
                .getOrCreate("plan_0123456789ABCDEFGHJKMNPQRS");
        var sigAlg = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
        var unsigned = "SAMLRequest=a%2Fb%2Bc&RelayState=x%20y&SigAlg=" + encode(sigAlg);
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(credentials.privateKey());
        signer.update(RedirectSignatureInput.fromRawQuery(unsigned));
        var query = unsigned + "&Signature=" + encode(Base64.getEncoder().encodeToString(signer.sign()));

        var verifier = new RedirectSignatureVerifier();
        assertTrue(verifier.isValid(query, credentials.certificate()));
        assertFalse(verifier.isValid(query.replace("x%20y", "x%20z"), credentials.certificate()));
        assertFalse(verifier.isValid(query + "&Signature=duplicate", credentials.certificate()));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
