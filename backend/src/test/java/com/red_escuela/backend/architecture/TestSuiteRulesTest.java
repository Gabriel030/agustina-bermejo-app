package com.red_escuela.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSuiteRulesTest {

    private static final JavaClasses TEST_CLASSES = TestSuiteRules.importTestClasses();
    private static final JavaClasses FIXTURES = TestSuiteRules.importFixtureClasses();

    @Test
    void domainAndPolicyTestsDoNotBootSpring() {
        FreezingArchRule.freeze(TestSuiteRules.domainAndPolicyTestsDoNotBootSpring()).check(TEST_CLASSES);
    }

    @Test
    void testsDoNotSleep() {
        FreezingArchRule.freeze(TestSuiteRules.testsDoNotSleep()).check(TEST_CLASSES);
    }

    @Test
    void testsDoNotDirtyTheContext() {
        FreezingArchRule.freeze(TestSuiteRules.testsDoNotDirtyTheContext()).check(TEST_CLASSES);
    }

    @Test
    void testsDoNotRunGlobalProcessesUnscoped() {
        FreezingArchRule.freeze(TestSuiteRules.testsDoNotRunGlobalProcessesUnscoped()).check(TEST_CLASSES);
    }

    @Test
    void domainRuleDetectsSpringBackedTest() {
        assertThatThrownBy(() -> TestSuiteRules.domainAndPolicyTestsDoNotBootSpring().check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("SpringBootDomainFixture");
    }

    @Test
    void sleepRuleDetectsThreadSleep() {
        assertThatThrownBy(() -> TestSuiteRules.testsDoNotSleep().check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("SleepingFixture");
    }

    @Test
    void dirtiesContextRuleDetectsAnnotation() {
        assertThatThrownBy(() -> TestSuiteRules.testsDoNotDirtyTheContext().check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("DirtyingFixture");
    }

    @Test
    void globalProcessRuleDetectsUnscopedSweep() {
        assertThatThrownBy(() -> TestSuiteRules.testsDoNotRunGlobalProcessesUnscoped().check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("GlobalSweepCallerFixture");
    }
}
