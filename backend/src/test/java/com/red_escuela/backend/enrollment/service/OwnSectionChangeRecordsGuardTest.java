package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.SectionEnrollmentRecordsPort;
import com.red_escuela.backend.enrollment.exception.SectionHasRecordsException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnSectionChangeRecordsGuardTest {

    private static final Long ENROLLMENT_ID = 7L;

    private final SectionEnrollmentRecordsPort withRecords = id -> true;
    private final SectionEnrollmentRecordsPort withoutRecords = id -> false;

    @Test
    void staffChangeIsNeverBlockedByRecords() {
        OwnSectionChangeRecordsGuard guard = new OwnSectionChangeRecordsGuard(List.of(withRecords));

        assertThatCode(() -> guard.requireNoRecords(EnrollmentChange.byStaff("Motivo del cambio de comision", 1L), ENROLLMENT_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void studentChangeIsBlockedWhenAnyPortReportsRecords() {
        OwnSectionChangeRecordsGuard guard = new OwnSectionChangeRecordsGuard(List.of(withoutRecords, withRecords));

        assertThatThrownBy(() -> guard.requireNoRecords(EnrollmentChange.byStudent(), ENROLLMENT_ID))
                .isInstanceOf(SectionHasRecordsException.class);
    }

    @Test
    void studentChangeIsAllowedWhenNoPortReportsRecords() {
        OwnSectionChangeRecordsGuard guard = new OwnSectionChangeRecordsGuard(List.of(withoutRecords, withoutRecords));

        assertThatCode(() -> guard.requireNoRecords(EnrollmentChange.byStudent(), ENROLLMENT_ID)).doesNotThrowAnyException();
    }
}
