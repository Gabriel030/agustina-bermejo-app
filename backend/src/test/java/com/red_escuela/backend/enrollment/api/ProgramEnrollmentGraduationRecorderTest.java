package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProgramEnrollmentGraduationRecorderTest {

    @Mock
    private ProgramEnrollmentRepository programEnrollmentRepository;
    @InjectMocks
    private ProgramEnrollmentGraduationRecorder recorder;

    @Test
    void recordGraduationSavesTheGraduatedEnrollment() {
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);

        recorder.recordGraduation(enrollment);

        verify(programEnrollmentRepository).save(enrollment);
    }
}
