package org.samlier.core.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OutcomeToVerdictTest {
    @ParameterizedTest
    @MethodSource("mapping")
    void mapsOutcomeThroughTheObligationLevel(
            Rfc2119Level level, Outcome outcome, Verdict expected) {
        var caseOutcome = outcome == Outcome.NOT_VERIFIED
                ? CaseOutcome.notVerified("timeout", "case.timeout")
                : new CaseOutcome(outcome, null, "test", "test.message", List.of(), Map.of());

        assertEquals(expected, Evaluator.toVerdict(level, caseOutcome));
    }

    @ParameterizedTest
    @MethodSource("prohibitiveLevels")
    void prohibitiveLevelsUseAlreadyNormalizedSatisfaction(
            Rfc2119Level level, Verdict expected) {
        assertEquals(expected, Evaluator.toVerdict(
                level, CaseOutcome.of(Outcome.VIOLATED, "prohibited-behavior-observed", List.of())));
    }

    @org.junit.jupiter.api.Test
    void requiresAReasonForNotVerified() {
        assertThrows(IllegalArgumentException.class,
                () -> new CaseOutcome(
                        Outcome.NOT_VERIFIED, null, "timeout", "test.timeout", List.of(), Map.of()));
    }

    private static Stream<Arguments> mapping() {
        return Stream.of(Rfc2119Level.MUST, Rfc2119Level.SHOULD, Rfc2119Level.MAY)
                .flatMap(level -> Stream.of(Outcome.values()).map(outcome -> Arguments.of(
                        level,
                        outcome,
                        expected(level.levelClass(), outcome))));
    }

    private static Verdict expected(Rfc2119Level.LevelClass levelClass, Outcome outcome) {
        return switch (outcome) {
            case SATISFIED -> Verdict.PASS;
            case SATISFIED_WITH_NOTE -> Verdict.WARNING;
            case VIOLATED -> switch (levelClass) {
                case MUST_CLASS -> Verdict.FAIL;
                case SHOULD_CLASS -> Verdict.WARNING;
                case MAY_CLASS -> Verdict.NOT_SUPPORTED;
            };
            case INDETERMINATE -> Verdict.INDETERMINATE;
            case INCONSISTENT -> Verdict.INCONSISTENT;
            case NOT_VERIFIED -> Verdict.NOT_VERIFIED;
        };
    }

    private static Stream<Arguments> prohibitiveLevels() {
        return Stream.of(
                Arguments.of(Rfc2119Level.MUST_NOT, Verdict.FAIL),
                Arguments.of(Rfc2119Level.SHOULD_NOT, Verdict.WARNING),
                Arguments.of(Rfc2119Level.NOT_RECOMMENDED, Verdict.WARNING));
    }
}
