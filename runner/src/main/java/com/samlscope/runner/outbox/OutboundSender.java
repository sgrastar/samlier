package com.samlscope.runner.outbox;

import java.util.Map;
import com.samlscope.core.caseexec.OutboundAction;

public interface OutboundSender {
    SendResult send(String runId, OutboundAction action, byte[] ephemeralCredential) throws Exception;

    record SendResult(boolean replayRejected, Map<String, Object> details, String transcriptEntryId) {
        public SendResult {
            details = Map.copyOf(details == null ? Map.of() : details);
        }
    }
}
