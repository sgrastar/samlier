package org.samlier.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AppConfigTest {
    @Test
    void hostedModeRequiresSeparatedOriginsAndSafeOutboundDefaults() {
        assertThrows(IllegalArgumentException.class, () -> config(
                URI.create("https://suite.example"), URI.create("https://suite.example"), false, false));
        assertThrows(IllegalArgumentException.class, () -> config(
                URI.create("https://app.example"), URI.create("https://peer.example"), true, false));
        assertThrows(IllegalArgumentException.class, () -> config(
                URI.create("https://app.example"), URI.create("https://peer.example"), false, true));
        assertThrows(IllegalArgumentException.class, () -> config(
                URI.create("http://app.example"), URI.create("https://peer.example"), false, false));
        assertDoesNotThrow(() -> config(
                URI.create("https://app.example"), URI.create("https://peer.example"), false, false));
    }

    @Test
    void baseUrlsAreOriginsWithoutCredentialsOrPaths() {
        assertThrows(IllegalArgumentException.class, () -> config(
                URI.create("https://user@app.example"), URI.create("https://peer.example"), false, false));
        assertThrows(IllegalArgumentException.class, () -> config(
                URI.create("https://app.example/base"), URI.create("https://peer.example"), false, false));
    }

    private AppConfig config(URI app, URI peer, boolean allowPrivate, boolean insecureTls) {
        return new AppConfig(AppConfig.Mode.HOSTED, app, peer, Path.of("data"), 8080,
                allowPrivate, insecureTls, true, "sha256:" + "a".repeat(64));
    }
}
