package org.samlier.runner;

import org.samlier.core.caseexec.CaseExecution;

/**
 * Identifies a case that first tries Suite-observed evidence and retains an approved manual
 * fallback when that evidence is inconclusive.
 *
 * <p>This distinction matters to plan reporting: the case belongs to Quick only after external
 * evidence actually resolved it. Before that, and after a manual answer, it remains a Full
 * self-attested case. Merely having an automatic fast path must not relabel a questionnaire as
 * externally verified.</p>
 */
public interface FallbackEvidenceCase {
    boolean resolvedFromExternalEvidence(CaseExecution execution);
}
