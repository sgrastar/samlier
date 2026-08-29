package org.samlier.api;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

public record AppConfig(
        Mode mode,
        URI publicBaseUrl,
        URI peerBaseUrl,
        Path dataDirectory,
        int httpPort,
        boolean outboundAllowPrivate,
        boolean outboundAllowInsecureTls,
        boolean publishEnabled,
        String suiteImageDigest) {

    public AppConfig {
        validateBaseUrl(publicBaseUrl, "publicBaseUrl");
        validateBaseUrl(peerBaseUrl, "peerBaseUrl");
        if (httpPort < 1 || httpPort > 65535) throw new IllegalArgumentException("Invalid HTTP port");
        if (mode == Mode.HOSTED && sameOrigin(publicBaseUrl, peerBaseUrl)) {
            throw new IllegalArgumentException("Hosted mode requires separate app and peer origins");
        }
        if (mode == Mode.HOSTED && (!"https".equalsIgnoreCase(publicBaseUrl.getScheme())
                || !"https".equalsIgnoreCase(peerBaseUrl.getScheme()))) {
            throw new IllegalArgumentException("Hosted mode requires HTTPS app and peer origins");
        }
        if (mode == Mode.HOSTED && outboundAllowPrivate) {
            throw new IllegalArgumentException("Hosted mode cannot allow private outbound destinations");
        }
        if (mode == Mode.HOSTED && outboundAllowInsecureTls) {
            throw new IllegalArgumentException("Hosted mode cannot disable outbound TLS verification");
        }
        suiteImageDigest = suiteImageDigest == null ? "" : suiteImageDigest;
        if (!suiteImageDigest.isEmpty() && !suiteImageDigest.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("suiteImageDigest must be a lowercase SHA-256 digest");
        }
        if (mode == Mode.HOSTED && suiteImageDigest.isEmpty()) {
            throw new IllegalArgumentException("Hosted mode requires SAMLIER_IMAGE_DIGEST");
        }
    }

    public AppConfig(
            Mode mode, URI publicBaseUrl, URI peerBaseUrl, Path dataDirectory, int httpPort,
            boolean outboundAllowPrivate, boolean outboundAllowInsecureTls, boolean publishEnabled) {
        this(mode, publicBaseUrl, peerBaseUrl, dataDirectory, httpPort, outboundAllowPrivate,
                outboundAllowInsecureTls, publishEnabled, "");
    }

    public static AppConfig fromEnvironment() { return from(System.getenv()); }

    static AppConfig from(Map<String, String> environment) {
        var mode = Mode.valueOf(environment.getOrDefault("SAMLIER_MODE", "selfhosted").toUpperCase(Locale.ROOT));
        var publicBase = URI.create(environment.getOrDefault("SAMLIER_PUBLIC_BASE_URL", "http://localhost:8080"));
        var peerBase = URI.create(environment.getOrDefault("SAMLIER_PEER_BASE_URL", publicBase.toString()));
        var data = Path.of(environment.getOrDefault("SAMLIER_DATA_DIR", "/data"));
        var port = Integer.parseInt(environment.getOrDefault("SAMLIER_HTTP_PORT", "8080"));
        var allowPrivate = Boolean.parseBoolean(environment.getOrDefault(
                "SAMLIER_OUTBOUND_ALLOW_PRIVATE", mode == Mode.SELFHOSTED ? "true" : "false"));
        var insecureTls = Boolean.parseBoolean(environment.getOrDefault("SAMLIER_OUTBOUND_ALLOW_INSECURE_TLS", "false"));
        var publish = Boolean.parseBoolean(environment.getOrDefault(
                "SAMLIER_PUBLISH_ENABLED", mode == Mode.HOSTED ? "true" : "false"));
        var imageDigest = environment.getOrDefault("SAMLIER_IMAGE_DIGEST", "");
        return new AppConfig(mode, publicBase, peerBase, data, port, allowPrivate, insecureTls, publish, imageDigest);
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static void validateBaseUrl(URI uri, String name) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException(name + " must be an absolute HTTP(S) URL without credentials, query, or fragment");
        }
        if (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())) {
            throw new IllegalArgumentException(name + " must be an origin without a path");
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    public enum Mode { SELFHOSTED, HOSTED }
}
