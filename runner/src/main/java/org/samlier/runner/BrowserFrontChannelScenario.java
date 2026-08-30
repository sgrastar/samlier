package org.samlier.runner;

import org.samlier.core.caseexec.CaseState;

/** Marker and presentation contract for a persisted scenario delivered through a real browser. */
public interface BrowserFrontChannelScenario {
    default boolean requiresFreshSession(CaseState state) { return false; }

    String instructionsEn(CaseState state);
}
