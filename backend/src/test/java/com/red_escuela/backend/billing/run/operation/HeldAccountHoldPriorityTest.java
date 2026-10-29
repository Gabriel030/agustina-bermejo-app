package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.collections.operation.HeldSubjectFinder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-356")
@Tag("D-459")
class HeldAccountHoldPriorityTest {
    private static final String INCONSISTENT = HeldSubjectFinder.INCONSISTENT_ACCOUNT_CODE;
    private static final String FAILED_BEFORE = HeldSubjectFinder.PREVIOUS_STEP_FAILED_CODE;

    private static String mergeInOrder(List<String> codes) {
        Map<Long, String> merged = new java.util.HashMap<>();
        BinaryOperator<String> stronger = HeldAccountLookup::strongerHoldOf;
        codes.forEach(code -> merged.merge(1L, code, stronger));
        return merged.get(1L);
    }

    @Test
    void theInconsistencyAlwaysWinsWhateverTheRowOrder() {
        List<List<String>> permutations = List.of(
                List.of(INCONSISTENT, FAILED_BEFORE), List.of(FAILED_BEFORE, INCONSISTENT),
                List.of(FAILED_BEFORE, FAILED_BEFORE, INCONSISTENT), List.of(FAILED_BEFORE, INCONSISTENT, FAILED_BEFORE),
                List.of(INCONSISTENT, FAILED_BEFORE, FAILED_BEFORE), List.of(INCONSISTENT, INCONSISTENT, FAILED_BEFORE));

        permutations.forEach(codes -> assertThat(mergeInOrder(new ArrayList<>(codes))).isEqualTo(INCONSISTENT));
    }

    @Test
    void aSingleKindOfHoldIsKept() {
        assertThat(mergeInOrder(List.of(FAILED_BEFORE, FAILED_BEFORE))).isEqualTo(FAILED_BEFORE);
        assertThat(mergeInOrder(List.of(INCONSISTENT))).isEqualTo(INCONSISTENT);
    }
}
