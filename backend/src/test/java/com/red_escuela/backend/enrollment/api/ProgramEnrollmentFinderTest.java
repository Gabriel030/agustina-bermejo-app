package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramEnrollmentFinderTest {

    @Mock
    private ProgramEnrollmentRepository programEnrollmentRepository;
    @InjectMocks
    private ProgramEnrollmentFinder finder;

    private ProgramEnrollment enrollmentOf(Long studentId) {
        Student student = mock(Student.class);
        when(student.getId()).thenReturn(studentId);
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        when(enrollment.getStudent()).thenReturn(student);
        return enrollment;
    }

    @Test
    void findReturnsEnrollment() {
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        when(programEnrollmentRepository.findById(4L)).thenReturn(Optional.of(enrollment));

        assertThat(finder.find(4L)).isSameAs(enrollment);
    }

    @Test
    void findThrowsWhenMissing() {
        when(programEnrollmentRepository.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.find(4L)).isInstanceOf(ProgramEnrollmentNotFoundException.class);
    }

    @Test
    void requireOwnedByReturnsEnrollmentOfTheStudent() {
        ProgramEnrollment enrollment = enrollmentOf(7L);
        when(programEnrollmentRepository.findById(4L)).thenReturn(Optional.of(enrollment));

        assertThat(finder.requireOwnedBy(4L, 7L)).isSameAs(enrollment);
    }

    @Test
    void requireOwnedByHidesEnrollmentOfAnotherStudent() {
        ProgramEnrollment enrollment = enrollmentOf(7L);
        when(programEnrollmentRepository.findById(4L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> finder.requireOwnedBy(4L, 8L)).isInstanceOf(ProgramEnrollmentNotFoundException.class);
    }

    @Test
    void findByStudentDelegatesToRepository() {
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        when(programEnrollmentRepository.findByStudentId(7L)).thenReturn(List.of(enrollment));

        assertThat(finder.findByStudent(7L)).containsExactly(enrollment);
    }

    @Test
    void findAllByIdDelegatesToRepository() {
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        when(programEnrollmentRepository.findAllById(List.of(4L, 5L))).thenReturn(List.of(enrollment));

        assertThat(finder.findAllById(List.of(4L, 5L))).containsExactly(enrollment);
    }

    private ProgramEnrollment billableEnrollment(Long id, Long studentId, Long programId) {
        Program program = mock(Program.class);
        when(program.getId()).thenReturn(programId);
        ProgramEnrollment enrollment = enrollmentOf(studentId);
        when(enrollment.getId()).thenReturn(id);
        when(enrollment.getProgram()).thenReturn(program);
        when(enrollment.getStatus()).thenReturn(ProgramEnrollmentStatus.ENROLLED);
        when(enrollment.getWithdrawalEffectiveDate()).thenReturn(LocalDate.of(2026, 3, 1));
        when(enrollment.getWithdrawalRegisteredOn()).thenReturn(LocalDate.of(2026, 2, 20));
        return enrollment;
    }

    @Test
    void requireByIdReturnsTheEnrollment() {
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        when(programEnrollmentRepository.findById(4L)).thenReturn(Optional.of(enrollment));

        assertThat(finder.requireById(4L)).isSameAs(enrollment);
    }

    @Test
    void requireByIdFailsWhenTheEnrollmentDoesNotExist() {
        when(programEnrollmentRepository.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.requireById(4L)).isInstanceOf(ProgramEnrollmentNotFoundException.class);
    }

    @Test
    void requireBillingViewByIdExposesTemplateAndWithdrawalDatesWithoutCohort() {
        ProgramEnrollmentRepository.BillingTemplateRef ref = mock(ProgramEnrollmentRepository.BillingTemplateRef.class);
        when(ref.getTemplateId()).thenReturn(12L);
        ProgramEnrollment enrollment = billableEnrollment(4L, 7L, 3L);
        when(programEnrollmentRepository.findById(4L)).thenReturn(Optional.of(enrollment));
        when(programEnrollmentRepository.findBillingTemplateRef(4L)).thenReturn(Optional.of(ref));

        assertThat(finder.requireBillingViewById(4L)).isEqualTo(new EnrollmentBillingView(4L, 7L, 3L, null, 12L,
                ProgramEnrollmentStatus.ENROLLED, null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 20)));
    }

    @Test
    void requireBillingViewByIdLeavesTemplateEmptyWhenThereIsNone() {
        ProgramEnrollment enrollment = billableEnrollment(4L, 7L, 3L);
        when(programEnrollmentRepository.findById(4L)).thenReturn(Optional.of(enrollment));
        when(programEnrollmentRepository.findBillingTemplateRef(4L)).thenReturn(Optional.empty());

        assertThat(finder.requireBillingViewById(4L).billingTemplateId()).isNull();
    }

    @Test
    void requireBillingViewByIdFailsWhenTheEnrollmentDoesNotExist() {
        when(programEnrollmentRepository.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.requireBillingViewById(4L)).isInstanceOf(ProgramEnrollmentNotFoundException.class);
    }

    @Test
    void findIdsByStudentIdReturnsOnlyTheIdsOfThatStudentEnrollments() {
        ProgramEnrollment first = mock(ProgramEnrollment.class);
        when(first.getId()).thenReturn(4L);
        ProgramEnrollment second = mock(ProgramEnrollment.class);
        when(second.getId()).thenReturn(6L);
        when(programEnrollmentRepository.findByStudentId(7L)).thenReturn(List.of(first, second));

        assertThat(finder.findIdsByStudentId(7L)).containsExactly(4L, 6L);
    }
}
