package org.samlier.store;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class Redactor {
    private static final Set<String> SECRET_FORM_KEYS =
            Set.of("password", "passwd", "pwd", "secret", "token", "otp", "pin");

    Sanitized sanitize(Map<String, List<String>> headers, byte[] body, String contentType,
                       String rawQuery, String url) {
        var cleanHeaders = new LinkedHashMap<String, List<String>>();
        headers.forEach((name, values) -> cleanHeaders.put(name, redactHeader(name, values)));
        var cleanBody = body.clone();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
            cleanBody = redactForm(new String(body, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        }
        var cleanQuery = rawQuery == null ? null : redactForm(rawQuery);
        return new Sanitized(Map.copyOf(cleanHeaders), cleanBody, cleanQuery, redactUrl(url, cleanQuery));
    }

    private List<String> redactHeader(String name, List<String> values) {
        var lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals("authorization") || lower.equals("proxy-authorization")) {
            return values.stream().map(value -> {
                var scheme = value.contains(" ") ? value.substring(0, value.indexOf(' ')) : "unknown";
                return "<redacted: " + scheme + ", " + value.getBytes(StandardCharsets.UTF_8).length + " bytes>";
            }).toList();
        }
        if (lower.equals("cookie") || lower.equals("set-cookie")) {
            return values.stream().map(this::redactCookies).toList();
        }
        return List.copyOf(values);
    }

    private String redactCookies(String value) {
        var parts = new ArrayList<String>();
        for (var part : value.split(";")) {
            var trimmed = part.trim();
            var equals = trimmed.indexOf('=');
            if (equals > 0) {
                var cookieValue = trimmed.substring(equals + 1);
                parts.add(trimmed.substring(0, equals + 1) + "<redacted: "
                        + cookieValue.getBytes(StandardCharsets.UTF_8).length + " bytes>");
            } else {
                parts.add(trimmed);
            }
        }
        return String.join("; ", parts);
    }

    private String redactForm(String value) {
        var parts = new ArrayList<String>();
        for (var part : value.split("&", -1)) {
            var equals = part.indexOf('=');
            var rawKey = equals < 0 ? part : part.substring(0, equals);
            var decodedKey = decodeOrRaw(rawKey).toLowerCase(Locale.ROOT);
            if (SECRET_FORM_KEYS.contains(decodedKey)) {
                var rawValue = equals < 0 ? "" : part.substring(equals + 1);
                var byteLength = decodeOrRaw(rawValue).getBytes(StandardCharsets.UTF_8).length;
                parts.add(rawKey + "=" + URLEncoder.encode("<redacted: " + byteLength + " bytes>", StandardCharsets.UTF_8));
            } else {
                parts.add(part);
            }
        }
        return String.join("&", parts);
    }

    private String redactUrl(String url, String alreadyRedactedQuery) {
        if (url == null) return null;
        var question = url.indexOf('?');
        if (question < 0) return url;
        var fragment = url.indexOf('#', question + 1);
        var rawQuery = url.substring(question + 1, fragment < 0 ? url.length() : fragment);
        var cleanQuery = alreadyRedactedQuery == null ? redactForm(rawQuery) : alreadyRedactedQuery;
        return url.substring(0, question + 1) + cleanQuery + (fragment < 0 ? "" : url.substring(fragment));
    }

    private String decodeOrRaw(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformedPercentEncoding) {
            return value;
        }
    }

    record Sanitized(Map<String, List<String>> headers, byte[] body, String rawQuery, String url) {}
}
