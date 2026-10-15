package com.red_escuela.backend.identity.domain;

import com.red_escuela.backend.identity.api.StudentEnrollmentActivityLookup;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentHasActiveEnrollmentsException;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("D-142")
@Tag("D-250")
class StudentWithdrawalGuardTest {

    private final StudentEnrollmentActivityLookup lookup = mock(StudentEnrollmentActivityLookup.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final StudentWithdrawalGuard guard = new StudentWithdrawalGuard(lookup, students);

    @Test
    void studentWithActiveEnrollmentsCannotBeWithdrawn() {
        when(students.findByIdForUpdate(7L)).thenReturn(Optional.of(new Student()));
        when(lookup.hasActiveEnrollments(7L)).thenReturn(true);

        assertThatThrownBy(() -> guard.lockStudentAndRequireWithdrawable(7L)).isInstanceOfSatisfying(StudentHasActiveEnrollmentsException.class,
                exception -> assertThat(exception.violations()).extracting("code").containsExactly("STUDENT_HAS_ACTIVE_ENROLLMENTS"));
    }

    @Test
    void studentWithoutActiveEnrollmentsCanBeWithdrawn() {
        when(students.findByIdForUpdate(7L)).thenReturn(Optional.of(new Student()));
        when(lookup.hasActiveEnrollments(7L)).thenReturn(false);

        assertThatCode(() -> guard.lockStudentAndRequireWithdrawable(7L)).doesNotThrowAnyException();
    }

    @Test
    void theStudentRowIsLockedBeforeLookingForActiveEnrollments() {
        when(students.findByIdForUpdate(7L)).thenReturn(Optional.of(new Student()));

        guard.lockStudentAndRequireWithdrawable(7L);

        InOrder order = inOrder(students, lookup);
        order.verify(students).findByIdForUpdate(7L);
        order.verify(lookup).hasActiveEnrollments(7L);
    }

    @Test
    void unknownStudentIsReportedWithoutLookingForEnrollments() {
        when(students.findByIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.lockStudentAndRequireWithdrawable(7L)).isInstanceOf(StudentNotFoundException.class);

        verifyNoInteractions(lookup);
    }
}
