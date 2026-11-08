package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityVerdict;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegularityEvaluatorTest {

    @Test
    void delegatesToTheRuleOfTheRequestedKind() {
        RegularityRule programRule = mock(RegularityRule.class);
        RegularityRule courseRule = mock(RegularityRule.class);
        when(programRule.kind()).thenReturn(RegularityKind.PROGRAM);
        when(courseRule.kind()).thenReturn(RegularityKind.COURSE);
        RegularityVerdict courseVerdict = new RegularityVerdict(RegularityKind.COURSE, true, List.of(), false, Instant.EPOCH);
        when(courseRule.evaluate(3L)).thenReturn(courseVerdict);

        RegularityEvaluator evaluator = new RegularityEvaluator(List.of(programRule, courseRule));

        assertThat(evaluator.evaluate(RegularityKind.COURSE, 3L)).isSameAs(courseVerdict);
    }
}
