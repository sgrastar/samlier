package com.samlscope.saml.logout;

import java.util.List;
import com.samlscope.saml.normal.SecureXml;

/** Transport-preserving semantic view used by SLO orchestration. */
public record LogoutExchange(Transport transport, String messageType, boolean asynchronous) {
    private static final String ASYNC = "urn:oasis:names:tc:SAML:2.0:protocol:ext:async-slo";

    public enum Transport { FRONT_CHANNEL, SOAP }
    public enum Event { SESSION_ESTABLISHED, LOGOUT_SENT, RESPONSE_OBSERVED, SESSION_DESTROYED }

    public static LogoutExchange parse(byte[] xml, Transport transport) {
        var document = SecureXml.parse(xml);
        var type = document.getDocumentElement().getLocalName();
        if (!type.equals("LogoutRequest") && !type.equals("LogoutResponse")) {
            throw new IllegalArgumentException("Not a SAML logout message");
        }
        return new LogoutExchange(transport, type,
                document.getElementsByTagNameNS(ASYNC, "Asynchronous").getLength() > 0);
    }

    public static boolean isSafeDestructiveOrder(List<Event> events) {
        return before(events, Event.SESSION_ESTABLISHED, Event.LOGOUT_SENT)
                && before(events, Event.LOGOUT_SENT, Event.RESPONSE_OBSERVED)
                && before(events, Event.RESPONSE_OBSERVED, Event.SESSION_DESTROYED);
    }

    private static boolean before(List<Event> events, Event first, Event second) {
        return events.indexOf(first) >= 0 && events.indexOf(first) < events.indexOf(second);
    }
}
