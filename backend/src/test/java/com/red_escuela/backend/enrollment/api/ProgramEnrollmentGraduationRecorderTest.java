package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramEnrollmentGraduationRecorderTest {

    @Mock
    private ProgramEnrollmentRepository programEnrollmentRepository;
    @Mock
    private EntityManager entityManager;
    @InjectMocks
    private ProgramEnrollmentGraduationRecorder recorder;

    @Test
    void recordGraduationSavesTheGraduatedEnrollment() {
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);

        recorder.recordGraduation(enrollment);

        verify(programEnrollmentRepository).save(enrollment);
    }

    @Test
    void lockForGraduationTakesTheRowLockAndThenRefreshesTheLockedEnrollment() {
        ProgramEnrollment requested = mock(ProgramEnrollment.class);
        ProgramEnrollment locked = mock(ProgramEnrollment.class);
        when(requested.getId()).thenReturn(10L);
        when(programEnrollmentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(locked));

        recorder.lockForGraduation(requested);

        InOrder order = inOrder(programEnrollmentRepository, entityManager);
        order.verify(programEnrollmentRepository).findByIdForUpdate(10L);
        order.verify(entityManager).refresh(locked);
    }

    @Test
    void lockForGraduationDoesNotRefreshWhenTheEnrollmentNoLongerExists() {
        ProgramEnrollment requested = mock(ProgramEnrollment.class);
        when(requested.getId()).thenReturn(10L);
        when(programEnrollmentRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());

        recorder.lockForGraduation(requested);

        verify(entityManager, never()).refresh(requested);
    }
}
