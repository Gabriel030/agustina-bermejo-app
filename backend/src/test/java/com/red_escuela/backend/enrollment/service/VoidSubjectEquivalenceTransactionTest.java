package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.exception.EquivalenceAlreadyVoidedException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectEquivalenceRepository;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoidSubjectEquivalenceTransactionTest {

    @Test
    void optimisticLockFailureOnConcurrentVoidBecomesAlreadyVoidedWithoutAuditOrEvent() {
        ProgramEnrollmentRepository enrollments = mock(ProgramEnrollmentRepository.class);
        SubjectEquivalenceRepository equivalences = mock(SubjectEquivalenceRepository.class);
        StaffPersonResolver staff = mock(StaffPersonResolver.class);
        AuditTrail auditTrail = mock(AuditTrail.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setId(1L);
        SubjectEquivalence equivalence = SubjectEquivalence.builder().id(7L).programEnrollment(enrollment).subjectId(3L).build();
        when(enrollments.findById(1L)).thenReturn(Optional.of(enrollment));
        when(equivalences.findById(7L)).thenReturn(Optional.of(equivalence));
        when(staff.currentStaffPersonId()).thenReturn(9L);
        when(equivalences.saveAndFlush(any())).thenThrow(new ObjectOptimisticLockingFailureException(SubjectEquivalence.class, 7L));
        VoidSubjectEquivalenceTransaction transaction = new VoidSubjectEquivalenceTransaction(enrollments, equivalences, mock(ProgramCurriculumFinder.class), staff,
                auditTrail, publisher, Clock.systemDefaultZone());

        assertThatThrownBy(() -> transaction.voidEquivalence(1L, 7L, "Anulación por carga errónea"))
                .isInstanceOf(EquivalenceAlreadyVoidedException.class);
        verify(auditTrail, never()).record(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }
}
