package com.samlscope.core.caseexec;

import java.util.regex.Pattern;

/** Strict parser for approved G2 case identifiers. */
public final class CaseIds {
    private static final Pattern CASE_ID = Pattern.compile(
            "^(IIP-[A-Z0-9]+)-([a-z]{1,2}[0-9]?)-(idp|sp)-([0-9]{2})$");

    private CaseIds() {}

    public static String obligationKey(String caseId) {
        if (caseId == null) throw new IllegalArgumentException("caseId must not be null");
        var matcher = CASE_ID.matcher(caseId);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid approved case ID: " + caseId);
        }
        return matcher.group(1) + "." + matcher.group(2);
    }
}
