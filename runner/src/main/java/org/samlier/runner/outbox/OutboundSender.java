package org.samlier.runner.outbox;

import java.util.Map;
import org.samlier.core.caseexec.OutboundAction;

public interface OutboundSender {
    SendResult send(OutboundAction action, byte[] ephemeralCredential) throws Exception;

    record SendResult(boolean replayRejected, Map<String, Object> details, String transcriptEntryId) {
        public SendResult {
            details = Map.copyOf(details == null ? Map.of() : details);
        }
    }
}
