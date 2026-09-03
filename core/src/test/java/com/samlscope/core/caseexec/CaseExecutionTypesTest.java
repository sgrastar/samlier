package com.samlscope.core.caseexec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CaseExecutionTypesTest {
    @Test
    void actionIdsAreDeterministicAndPhaseScoped() {
        var first = ActionIds.derive("run-1", "case-1", "send", 0);
        assertEquals(first, ActionIds.derive("run-1", "case-1", "send", 0));
        assertFalse(first.equals(ActionIds.derive("run-1", "case-1", "send", 1)));
        assertFalse(first.equals(ActionIds.derive("run-1", "case-1", "wait", 0)));
    }

    @Test
    void caseStateIsDeeplyImmutableAndJsonOnly() {
        var nested = new ArrayList<>(List.of("original"));
        var source = new HashMap<String, Object>();
        source.put("nested", nested);
        source.put("optional", null);
        var state = new CaseState("phase", source);
        nested.add("mutated");
        source.put("new", true);

        assertEquals(List.of("original"), state.data().get("nested"));
        assertFalse(state.data().containsKey("new"));
        assertEquals(null, state.data().get("optional"));
        assertThrows(IllegalArgumentException.class,
                () -> new CaseState("phase", Map.of("credential", new char[] {'s', 'e', 'c', 'r', 'e', 't'})));
    }

    @Test
    void outboundPayloadIsDefensivelyCopied() {
        var source = new byte[] {1, 2, 3};
        var action = new OutboundAction(
                "action_0123456789abcdef0123456789abcdef",
                OutboundKind.AUTHN_REQUEST,
                source,
                URI.create("https://idp.example/sso"),
                false);
        source[0] = 9;
        var returned = action.payload();
        returned[1] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, action.payload());
    }

    @Test
    void everyWaitingStepRequiresAPositiveTtl() {
        assertThrows(IllegalArgumentException.class, () -> new CaseStep.AwaitConfig(
                CaseState.initial(), List.of(), "configure.feature", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new CaseStep.AwaitInbound(
                CaseState.initial(), List.of(), new InboundMatcher("response", Map.of()), Duration.ofSeconds(-1)));
    }

    @Test
    void retrySafetyIsFixedByOutboundKind() {
        assertEquals(OutboundKind.Retry.SAFE, OutboundKind.METADATA_FETCH.retry());
        assertEquals(OutboundKind.Retry.SAFE, OutboundKind.MDQ_FETCH.retry());
        assertTrue(StreamSupport.unsafeKinds().stream().allMatch(
                kind -> kind.retry() == OutboundKind.Retry.UNSAFE));
    }

    private static final class StreamSupport {
        private static List<OutboundKind> unsafeKinds() {
            return List.of(
                    OutboundKind.AUTHN_REQUEST,
                    OutboundKind.LOGOUT_REQUEST,
                    OutboundKind.ECP_SOAP,
                    OutboundKind.SOAP_SLO);
        }
    }
}
