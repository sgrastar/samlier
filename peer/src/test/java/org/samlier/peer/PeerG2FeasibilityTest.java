package org.samlier.peer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;

/** Production-boundary probes for the optional second Test IdP. */
final class PeerG2FeasibilityTest {
    @Test
    void s4SecondaryPeerHasDistinctDeterministicEntityId() {
        var base = URI.create("https://peer.example.test/");
        var first = PeerIdentity.secondaryIdp(base, "plan-1");
        assertEquals(first, PeerIdentity.secondaryIdp(base, "plan-1"));
        assertNotEquals(PeerIdentity.primary(base, "plan-1"), first);
    }

    @Test
    void s4SecondaryPeerHasIndependentMetadataRegistrationPath() {
        var base = URI.create("https://peer.example.test/");
        assertEquals(URI.create("https://peer.example.test/p/plan-1/idp/secondary/metadata"),
                PeerIdentity.metadata(PeerIdentity.secondaryIdp(base, "plan-1")));
        assertNotEquals(PeerIdentity.metadata(PeerIdentity.primary(base, "plan-1")),
                PeerIdentity.metadata(PeerIdentity.secondaryIdp(base, "plan-1")));
    }
}
