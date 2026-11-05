package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.testsupport.TermFixtures;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.entity.TermEnrollmentOverride;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class TermResponseTest {

    @Test
    void termWithoutOverrideExposesNoEnrollmentOverride() {
        assertThat(TermResponse.from(TermFixtures.newTerm()).enrollmentOverride()).isNull();
    }

    @Test
    void termForcedOpenExposesOpenEnrollmentOverride() {
        assertThat(TermResponse.from(termWith(TermEnrollmentOverride.OPEN)).enrollmentOverride())
                .isEqualTo(TermEnrollmentOverride.OPEN);
    }

    @Test
    void termForcedClosedExposesClosedEnrollmentOverride() {
        assertThat(TermResponse.from(termWith(TermEnrollmentOverride.CLOSED)).enrollmentOverride())
                .isEqualTo(TermEnrollmentOverride.CLOSED);
    }

    private static Term termWith(TermEnrollmentOverride override) {
        Term term = TermFixtures.newTerm();
        ReflectionTestUtils.setField(term, "enrollmentOverride", override);
        return term;
    }
}
