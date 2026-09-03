package com.samlscope.core.caseexec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CaseIdsTest {
    @Test
    void extractsObligationKeyWithoutGuessingFromArbitraryText() {
        assertEquals("IIP-G03.a", CaseIds.obligationKey("IIP-G03-a-idp-01"));
        assertEquals("IIP-SSO01.aa", CaseIds.obligationKey("IIP-SSO01-aa-sp-02"));
        assertEquals("IIP-IDP17.a1", CaseIds.obligationKey("IIP-IDP17-a1-idp-99"));
    }

    @Test
    void rejectsAnythingOutsideTheApprovedCaseIdGrammar() {
        for (var invalid : new String[] {
                "IIP-G03.a-idp-01", "IIP-G03-a-peer-01", "IIP-G03-a-idp-1",
                "IIP-G03-aaa-idp-01", "prefix-IIP-G03-a-idp-01", "", null}) {
            assertThrows(IllegalArgumentException.class, () -> CaseIds.obligationKey(invalid));
        }
    }
}
