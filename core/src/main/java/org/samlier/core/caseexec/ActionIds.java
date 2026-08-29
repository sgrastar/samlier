package org.samlier.core.caseexec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ActionIds {
    private ActionIds() {}

    public static String derive(String runId, String caseId, String phase, int sequence) {
        requireText(runId, "runId");
        requireText(caseId, "caseId");
        requireText(phase, "phase");
        if (sequence < 0) throw new IllegalArgumentException("sequence must not be negative");
        var material = String.join("\u001f", runId, caseId, phase, Integer.toString(sequence));
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return "action_" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
