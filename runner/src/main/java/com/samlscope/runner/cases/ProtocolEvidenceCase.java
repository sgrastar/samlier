package com.samlscope.runner.cases;

import java.util.List;
import java.util.Map;
import com.samlscope.core.caseexec.CaseContext;

/** A waiting case whose outcome can be derived from Suite-recorded protocol evidence. */
public interface ProtocolEvidenceCase extends com.samlscope.runner.ExternallyObservedCase {
    EvidenceStatus evidenceStatus(CaseContext context);

    record EvidenceStatus(
            boolean ready,
            List<String> requiredObservations,
            List<String> completedObservations,
            Map<String, Object> details) {
        public EvidenceStatus {
            requiredObservations = List.copyOf(requiredObservations);
            completedObservations = List.copyOf(completedObservations);
            details = Map.copyOf(details);
        }
    }
}
