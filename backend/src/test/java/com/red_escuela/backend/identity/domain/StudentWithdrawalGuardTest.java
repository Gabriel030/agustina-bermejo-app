package com.red_escuela.backend.identity.domain;

import com.red_escuela.backend.identity.api.StudentEnrollmentActivityLookup;
import com.red_escuela.backend.identity.exception.StudentHasActiveEnrollmentsException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-142")
@Tag("D-250")
class StudentWithdrawalGuardTest {

    private final StudentEnrollmentActivityLookup lookup = mock(StudentEnrollmentActivityLookup.class);
    private final com.red_escuela.backend.identity.repository.StudentRepository students = mock(com.red_escuela.backend.identity.repository.StudentRepository.class);
    private final StudentWithdrawalGuard guard = new StudentWithdrawalGuard(lookup, students);

    @Test
    void studentWithActiveEnrollmentsCannotBeWithdrawn() {
        when(lookup.hasActiveEnrollments(7L)).thenReturn(true);

        assertThatThrownBy(() -> guard.requireStudentWithdrawable(7L)).isInstanceOfSatisfying(StudentHasActiveEnrollmentsException.class,
                exception -> assertThat(exception.violations()).extracting("code").containsExactly("STUDENT_HAS_ACTIVE_ENROLLMENTS"));
    }

    @Test
    void studentWithoutActiveEnrollmentsCanBeWithdrawn() {
        when(lookup.hasActiveEnrollments(7L)).thenReturn(false);

        assertThatCode(() -> guard.requireStudentWithdrawable(7L)).doesNotThrowAnyException();
    }

    @Test
    void theStudentRowIsLockedBeforeLookingForActiveEnrollments() {
        guard.requireStudentWithdrawable(7L);

        verify(students).findByIdForUpdate(7L);
    }
}
