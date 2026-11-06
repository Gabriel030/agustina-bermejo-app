package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import com.red_escuela.backend.attendance.exception.CannotJustifyException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.exception.JustificationDeadlineExpiredException;
import com.red_escuela.backend.attendance.service.JustificationDeadlinePolicyTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static com.red_escuela.backend.attendance.api.StudentAttendanceReaderTest.attendanceOf;
import static com.red_escuela.backend.attendance.api.StudentAttendanceReaderTest.programEnrollment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceProofSubmitterTest {

    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
    private final AttendanceProofSubmitter submitter = submitterOn(LocalDate.of(2026, 3, 9));

    private AttendanceProofSubmitter submitterOn(LocalDate today) {
        return new AttendanceProofSubmitter(attendanceRepository, JustificationDeadlinePolicyTest.policy(today, 5));
    }

    @Test
    void marksAnAbsenceAsPendingJustificationWithReasonAndAttachment() {
        Attendance attendance = attendanceOf(programEnrollment(7L, 3L), null);
        attendance.setClassDate(LocalDate.of(2026, 3, 6));
        attendance.setId(11L);
        when(attendanceRepository.findById(11L)).thenReturn(Optional.of(attendance));
        when(attendanceRepository.save(attendance)).thenReturn(attendance);

        StudentAttendanceView view = submitter.submitProof(11L, "Fiebre", "ruta/comprobante.pdf");

        assertThat(view.status()).isEqualTo("JUSTIFICATION_PENDING");
        assertThat(view.justificationReason()).isEqualTo("Fiebre");
        assertThat(view.justificationAttachmentUrl()).isEqualTo("ruta/comprobante.pdf");
        assertThat(view.enrollmentId()).isEqualTo(7L);
    }

    @Test
    void refusesToJustifyAnAttendanceThatIsNotAbsent() {
        Attendance attendance = attendanceOf(programEnrollment(7L, 3L), null);
        attendance.setStatus(AttendanceStatus.PRESENT);
        when(attendanceRepository.findById(11L)).thenReturn(Optional.of(attendance));

        assertThatThrownBy(() -> submitter.submitProof(11L, "Fiebre", "ruta.pdf"))
                .isInstanceOf(CannotJustifyException.class);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void rejectsAnUnknownAttendanceAsNotFound() {
        when(attendanceRepository.findById(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submitter.submitProof(11L, "Fiebre", "ruta.pdf"))
                .isInstanceOf(AttendanceNotFoundException.class);
    }

    @Test
    void submitProofFailsAfterDeadline() {
        Attendance attendance = attendanceOf(programEnrollment(7L, 3L), null);
        attendance.setClassDate(LocalDate.of(2026, 3, 6));
        when(attendanceRepository.findById(11L)).thenReturn(Optional.of(attendance));
        AttendanceProofSubmitter late = submitterOn(LocalDate.of(2026, 3, 14));

        assertThatThrownBy(() -> late.submitProof(11L, "Fiebre", "ruta.pdf"))
                .isInstanceOf(JustificationDeadlineExpiredException.class);
        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void submitProofSucceedsOnTheLastDayOfTheDeadline() {
        Attendance attendance = attendanceOf(programEnrollment(7L, 3L), null);
        attendance.setClassDate(LocalDate.of(2026, 3, 6));
        when(attendanceRepository.findById(11L)).thenReturn(Optional.of(attendance));
        when(attendanceRepository.save(attendance)).thenReturn(attendance);

        StudentAttendanceView view = submitterOn(LocalDate.of(2026, 3, 13)).submitProof(11L, "Fiebre", "ruta.pdf");

        assertThat(view.status()).isEqualTo("JUSTIFICATION_PENDING");
    }
}
