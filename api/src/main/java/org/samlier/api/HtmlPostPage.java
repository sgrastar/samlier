package org.samlier.api;

import java.net.URI;

final class HtmlPostPage {
    private HtmlPostPage() {}

    static String render(URI destination, String samlResponse, String relayState, String nonce) {
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="referrer" content="no-referrer">
                <title>Continue SAML sign-in</title></head>
                <body><p>Continuing to the service provider…</p>
                <form method="post" action="%s">
                  <input type="hidden" name="SAMLResponse" value="%s">
                  %s
                  <noscript><button type="submit">Continue</button></noscript>
                </form>
                <script nonce="%s">document.forms[0].submit()</script></body></html>
                """.formatted(escape(destination.toString()), escape(samlResponse),
                relayState == null ? "" : "<input type=\"hidden\" name=\"RelayState\" value=\""
                        + escape(relayState) + "\">", escape(nonce));
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
