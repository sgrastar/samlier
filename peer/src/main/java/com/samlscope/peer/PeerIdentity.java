package com.samlscope.peer;

import java.net.URI;

/** Deterministic identities for primary and optional secondary Test IdPs. */
public final class PeerIdentity {
    private PeerIdentity() {}

    public static URI primary(URI peerBaseUrl, String planId) {
        return peerBaseUrl.resolve("/p/" + planId);
    }

    public static URI secondaryIdp(URI peerBaseUrl, String planId) {
        return peerBaseUrl.resolve("/p/" + planId + "/idp/secondary");
    }

    public static URI metadata(URI entityId) {
        return URI.create(entityId.toString() + "/metadata");
    }
}
