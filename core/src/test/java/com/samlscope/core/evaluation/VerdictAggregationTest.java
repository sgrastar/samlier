package com.samlscope.core.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VerdictAggregationTest {
    private static final Map<Verdict, Integer> FROZEN_ORDER = Map.of(
            Verdict.FAIL, 10,
            Verdict.INCONSISTENT, 9,
            Verdict.ERROR, 8,
            Verdict.INDETERMINATE, 7,
            Verdict.NOT_VERIFIED, 6,
            Verdict.WARNING, 5,
            Verdict.PASS, 4,
            Verdict.NOT_SUPPORTED, 3,
            Verdict.NOT_OBSERVABLE, 2,
            Verdict.NOT_APPLICABLE, 1);

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @MethodSource("allPairs")
    void allOneHundredPairsFollowTheFrozenSeverityOrder(
            Verdict left, Verdict right, Verdict expected) {
        assertEquals(expected, Evaluator.aggregate(left, right));
        assertEquals(expected, Evaluator.aggregate(right, left));
    }

    private static Stream<Arguments> allPairs() {
        return Stream.of(Verdict.values()).flatMap(left ->
                Stream.of(Verdict.values()).map(right -> Arguments.of(
                        left,
                        right,
                        FROZEN_ORDER.get(left) >= FROZEN_ORDER.get(right) ? left : right)));
    }
}
