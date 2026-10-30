package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProgramEnrollmentOwnershipCheckerTest {

    private final ProgramEnrollmentFinder finder = mock(ProgramEnrollmentFinder.class);
    private final ProgramEnrollmentOwnershipChecker checker = new ProgramEnrollmentOwnershipChecker(finder);

    @Test
    void ownershipIsDelegatedToTheEnrollmentApiWithBothIds() {
        checker.requireOwnedBy(5L, 9L);

        verify(finder).requireOwnedBy(5L, 9L);
    }

    @Test
    void anEnrollmentOfAnotherStudentIsReportedAsNotFound() {
        doThrow(new ProgramEnrollmentNotFoundException(5L)).when(finder).requireOwnedBy(5L, 9L);

        assertThatThrownBy(() -> checker.requireOwnedBy(5L, 9L)).isInstanceOf(ProgramEnrollmentNotFoundException.class);
    }
}
