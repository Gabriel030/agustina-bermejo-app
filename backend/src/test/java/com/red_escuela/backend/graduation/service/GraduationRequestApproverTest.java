package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.enrollment.api.ProgramEnrollmentGraduationRecorder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.exception.GraduationRequirementsNotMetException;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraduationRequestApproverTest {

    private final GraduationEligibilityChecker eligibilityChecker = mock(GraduationEligibilityChecker.class);
    private final ProgramEnrollmentGraduationRecorder recorder = mock(ProgramEnrollmentGraduationRecorder.class);
    private final GraduationRequestRepository requestRepository = mock(GraduationRequestRepository.class);
    private final GraduationRequestApprover approver =
            new GraduationRequestApprover(eligibilityChecker, recorder, requestRepository);

    private ProgramEnrollment enrollmentWithStudent(Long enrollmentId, Long studentId) {
        Student student = new Student();
        student.setId(studentId);
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setId(enrollmentId);
        enrollment.setStudent(student);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        return enrollment;
    }

    private GraduationRequest verifiedRequestFor(ProgramEnrollment enrollment) {
        GraduationRequest request = new GraduationRequest();
        request.setProgramEnrollment(enrollment);
        request.setStatus(GraduationRequestStatus.ADMIN_VERIFIED);
        return request;
    }

    @Test
    void anEligibleVerifiedRequestReturnsTheStudentIdOfItsEnrollment() {
        ProgramEnrollment enrollment = enrollmentWithStudent(10L, 77L);
        when(eligibilityChecker.isEligibleToGraduate(10L)).thenReturn(true);

        assertThat(approver.requireApprovableAndFindStudentId(verifiedRequestFor(enrollment))).isEqualTo(77L);
    }

    @Test
    void aNotEligibleEnrollmentFailsWithGraduationRequirementsNotMet() {
        ProgramEnrollment enrollment = enrollmentWithStudent(10L, 77L);
        when(eligibilityChecker.isEligibleToGraduate(10L)).thenReturn(false);

        assertThatThrownBy(() -> approver.requireApprovableAndFindStudentId(verifiedRequestFor(enrollment)))
                .isInstanceOf(GraduationRequirementsNotMetException.class);
    }

    @Test
    void recordingTheApprovalSavesTheRequestBeforeRecordingTheGraduationOfItsEnrollment() {
        ProgramEnrollment enrollment = enrollmentWithStudent(10L, 77L);
        GraduationRequest request = verifiedRequestFor(enrollment);

        approver.recordApproval(request, new Person(), LocalDateTime.of(2026, 10, 30, 10, 0));

        InOrder inOrder = Mockito.inOrder(requestRepository, recorder);
        inOrder.verify(requestRepository).save(request);
        inOrder.verify(recorder).recordGraduation(enrollment);
        assertThat(request.getStatus()).isEqualTo(GraduationRequestStatus.APPROVED);
        assertThat(enrollment.getStatus()).isEqualTo(ProgramEnrollmentStatus.COMPLETED);
    }

    @Test
    void aRequestNotAwaitingTheDirectorDecisionRecordsNothing() {
        GraduationRequest request = verifiedRequestFor(enrollmentWithStudent(10L, 77L));
        request.setStatus(GraduationRequestStatus.REQUESTED);

        assertThatThrownBy(() -> approver.recordApproval(request, new Person(), LocalDateTime.now()))
                .isInstanceOf(RuntimeException.class);

        verify(recorder, never()).recordGraduation(any());
        verify(requestRepository, never()).save(any());
    }
}
