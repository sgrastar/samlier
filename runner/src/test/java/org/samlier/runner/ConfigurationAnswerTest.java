package org.samlier.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.samlier.core.caseexec.CaseEvent;

class ConfigurationAnswerTest {
    @Test
    void mapsOnlyTheFixedPromptVocabularyToCaseEvents() {
        assertInstanceOf(CaseEvent.ConfigConfirmed.class,
                ConfigurationAnswer.parse("confirmed").event(""));

        var absent = assertInstanceOf(
                CaseEvent.ConfigUnavailable.class,
                ConfigurationAnswer.parse("capability_absent").event("No setting exists"));
        assertEquals(CaseEvent.ConfigurationIssue.CAPABILITY_ABSENT, absent.issue());
        assertEquals("No setting exists", absent.note());

        assertEquals(
                CaseEvent.ConfigurationIssue.TARGET_CONFIG_UNAVAILABLE,
                assertInstanceOf(
                        CaseEvent.ConfigUnavailable.class,
                        ConfigurationAnswer.parse("target_config_unavailable").event("No permission")).issue());
        assertEquals(
                CaseEvent.ConfigurationIssue.CAPABILITY_UNDETERMINED,
                assertInstanceOf(
                        CaseEvent.ConfigUnavailable.class,
                        ConfigurationAnswer.parse("capability_undetermined").event("Unknown")).issue());
    }

    @Test
    void rejectsClientVerdictsUnknownValuesAndAmbiguousConfirmedNotes() {
        assertThrows(IllegalArgumentException.class, () -> ConfigurationAnswer.parse("FAIL"));
        assertThrows(IllegalArgumentException.class, () -> ConfigurationAnswer.parse("not_applicable"));
        assertThrows(IllegalArgumentException.class, () -> ConfigurationAnswer.CONFIRMED.event("but maybe not"));
    }
}
