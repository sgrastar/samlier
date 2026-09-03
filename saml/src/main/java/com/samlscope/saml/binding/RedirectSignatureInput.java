package com.samlscope.saml.binding;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import com.samlscope.saml.normal.SamlException;

/**
 * Builds the HTTP-Redirect binding signature input from the raw query octets.
 * Values are deliberately not URL-decoded and reconstructed.
 */
public final class RedirectSignatureInput {
    private RedirectSignatureInput() {}

    public static byte[] fromRawQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            throw new SamlException("Raw query string is required");
        }
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(rawQuery)) {
            throw new SamlException("Raw query string must contain ASCII octets only");
        }
        Map<String, String> encodedPairs = new LinkedHashMap<>();
        for (var pair : rawQuery.split("&", -1)) {
            var equals = pair.indexOf('=');
            var name = equals < 0 ? pair : pair.substring(0, equals);
            if (name.equals("SAMLRequest") || name.equals("SAMLResponse")
                    || name.equals("RelayState") || name.equals("SigAlg")) {
                if (equals < 0) {
                    throw new SamlException("Signed query parameter lacks '=': " + name);
                }
                if (encodedPairs.putIfAbsent(name, pair) != null) {
                    throw new SamlException("Duplicate signed query parameter: " + name);
                }
            }
        }
        var messageName = encodedPairs.containsKey("SAMLRequest") ? "SAMLRequest" : "SAMLResponse";
        if (!encodedPairs.containsKey(messageName) || !encodedPairs.containsKey("SigAlg")) {
            throw new SamlException("Raw query must contain one SAML message and SigAlg");
        }
        if (encodedPairs.containsKey("SAMLRequest") && encodedPairs.containsKey("SAMLResponse")) {
            throw new SamlException("Raw query cannot contain both SAMLRequest and SAMLResponse");
        }
        var signed = new StringBuilder(encodedPairs.get(messageName));
        if (encodedPairs.containsKey("RelayState")) {
            signed.append('&').append(encodedPairs.get("RelayState"));
        }
        signed.append('&').append(encodedPairs.get("SigAlg"));
        return signed.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
