package com.samlscope.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
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

    @Test
    void legacyEnvironmentNamesPreserveHostedSecurityDuringMigration() {
        var digest = "sha256:" + "b".repeat(64);
        var config = AppConfig.from(Map.of(
                "SAMLIER_MODE", "hosted",
                "SAMLIER_PUBLIC_BASE_URL", "https://app.example",
                "SAMLIER_PEER_BASE_URL", "https://peer.example",
                "SAMLIER_DATA_DIR", "/legacy-data",
                "SAMLIER_HTTP_PORT", "8443",
                "SAMLIER_OUTBOUND_ALLOW_PRIVATE", "false",
                "SAMLIER_OUTBOUND_ALLOW_INSECURE_TLS", "false",
                "SAMLIER_PUBLISH_ENABLED", "true",
                "SAMLIER_IMAGE_DIGEST", digest,
                "SAMLIER_TRUSTED_PROXY_ADDRESS", "172.30.0.1"));

        assertEquals(AppConfig.Mode.HOSTED, config.mode());
        assertEquals(URI.create("https://app.example"), config.publicBaseUrl());
        assertEquals(URI.create("https://peer.example"), config.peerBaseUrl());
        assertEquals(Path.of("/legacy-data"), config.dataDirectory());
        assertEquals(8443, config.httpPort());
        assertEquals(false, config.outboundAllowPrivate());
        assertEquals(false, config.outboundAllowInsecureTls());
        assertEquals(true, config.publishEnabled());
        assertEquals(digest, config.suiteImageDigest());
        assertEquals("172.30.0.1", config.trustedProxyAddress());
    }

    @Test
    void conflictingCurrentAndLegacyEnvironmentNamesFailClosed() {
        for (var suffix : java.util.List.of(
                "MODE", "PUBLIC_BASE_URL", "PEER_BASE_URL", "DATA_DIR", "HTTP_PORT",
                "OUTBOUND_ALLOW_PRIVATE", "OUTBOUND_ALLOW_INSECURE_TLS",
                "PUBLISH_ENABLED", "IMAGE_DIGEST", "TRUSTED_PROXY_ADDRESS")) {
            var environment = new HashMap<String, String>();
            environment.put("SAMLSCOPE_" + suffix, "current");
            environment.put("SAMLIER_" + suffix, "legacy");
            assertThrows(IllegalArgumentException.class, () -> AppConfig.from(environment), suffix);
        }
    }

    @Test
    void equalCurrentAndLegacyEnvironmentNamesAreAccepted() {
        assertEquals(AppConfig.Mode.SELFHOSTED,
                AppConfig.from(Map.of("SAMLSCOPE_MODE", "selfhosted", "SAMLIER_MODE", "selfhosted")).mode());
    }

    @Test
    void hostedEnvironmentRequiresOneExplicitNumericTrustedProxy() {
        var environment = new HashMap<String, String>();
        environment.put("SAMLSCOPE_MODE", "hosted");
        environment.put("SAMLSCOPE_PUBLIC_BASE_URL", "https://app.example");
        environment.put("SAMLSCOPE_PEER_BASE_URL", "https://peer.example");
        environment.put("SAMLSCOPE_IMAGE_DIGEST", "sha256:" + "a".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> AppConfig.from(environment));

        environment.put("SAMLSCOPE_TRUSTED_PROXY_ADDRESS", "proxy.example");
        assertThrows(IllegalArgumentException.class, () -> AppConfig.from(environment));
        environment.put("SAMLSCOPE_TRUSTED_PROXY_ADDRESS", "172.30.0.1");
        assertEquals("172.30.0.1", AppConfig.from(environment).trustedProxyAddress());
    }

    private AppConfig config(URI app, URI peer, boolean allowPrivate, boolean insecureTls) {
        return new AppConfig(AppConfig.Mode.HOSTED, app, peer, Path.of("data"), 8080,
                allowPrivate, insecureTls, true, "sha256:" + "a".repeat(64), "127.0.0.1");
    }
}
