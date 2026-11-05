package com.red_escuela.backend.architecture;

import com.red_escuela.backend.shared.time.RawNowRule;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RawNowInBillingTest {

    private static final JavaClasses MAIN_CLASSES = RawNowRule.importMainClasses();

    @Test
    void noRawNowInBilling() {
        assertThat(RawNowRule.guardedViolations(MAIN_CLASSES)).isEmpty();

        Set<String> offenders = RawNowRule.offendingClassesOutsideGuard(MAIN_CLASSES);
        assertThat(offenders).isSubsetOf(RawNowRule.frozenBaseline());
    }
}
