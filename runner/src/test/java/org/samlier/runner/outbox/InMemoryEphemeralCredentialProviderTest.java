package org.samlier.runner.outbox;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class InMemoryEphemeralCredentialProviderTest {
    @Test
    void credentialsAreClonedAndConsumedExactlyOnce() {
        var provider = new InMemoryEphemeralCredentialProvider();
        var source = "user:secret".getBytes(StandardCharsets.UTF_8);
        provider.put("run", "action", source);
        source[0] = 0;

        assertArrayEquals("user:secret".getBytes(StandardCharsets.UTF_8),
                provider.credentialFor("run", "action").orElseThrow());
        assertTrue(provider.credentialFor("run", "action").isEmpty());
    }

    @Test
    void discardRemovesAnUnusedCredential() {
        var provider = new InMemoryEphemeralCredentialProvider();
        provider.put("run", "action", new byte[] {1, 2, 3});
        provider.discard("run", "action");
        assertTrue(provider.credentialFor("run", "action").isEmpty());
    }
}
