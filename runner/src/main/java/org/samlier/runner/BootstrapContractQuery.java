package org.samlier.runner;

import java.net.URI;
import java.util.List;

/** Read-only projection of shared setup prerequisites for pending CONFIG cases. */
@FunctionalInterface
public interface BootstrapContractQuery {
    List<BootstrapContract> contracts(String runId);

    record BootstrapContract(
            String id,
            String title,
            String description,
            Kind kind,
            Readiness readiness,
            URI setupUrl,
            String setupInstruction,
            int pendingCases,
            List<String> caseIds) {
        public BootstrapContract {
            caseIds = List.copyOf(caseIds);
            if (pendingCases != caseIds.size()) {
                throw new IllegalArgumentException("pendingCases must match caseIds");
            }
        }
    }

    enum Kind { STANDARD_METADATA, OPERATOR_POLICY }
    enum Readiness { SETUP_REQUIRED, FETCH_OBSERVED, MANUAL_ONLY }
}
