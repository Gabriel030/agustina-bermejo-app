package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.api.AttendanceProofSubmitter;
import com.red_escuela.backend.attendance.dto.AttendanceResponse;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import com.red_escuela.backend.attendance.exception.CannotJustifyException;
import com.red_escuela.backend.attendance.exception.JustificationDeadlineExpiredException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.storage.FileStorageService;
import com.red_escuela.backend.shared.storage.UnsupportedFileTypeException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherAttendanceProofRecorderTest {

    private static final long ATTENDANCE_ID = 11L;
    private static final long INSTRUCTOR_ID = 5L;
    private static final long STUDENT_ID = 3L;
    private static final LocalDate CLASS_DATE = LocalDate.of(2026, 3, 6);

    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
    private final AttendanceFinder attendanceFinder = mock(AttendanceFinder.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);

    private TeacherAttendanceProofRecorder recorderOn(LocalDate today) {
        AttendanceProofSubmitter submitter = new AttendanceProofSubmitter(
                attendanceRepository, JustificationDeadlinePolicyTest.policy(today, 5));
        return new TeacherAttendanceProofRecorder(attendanceFinder, submitter, fileStorageService);
    }

    private Attendance absence() {
        Student student = new Student();
        student.setId(STUDENT_ID);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setId(7L);
        enrollment.setStudent(student);
        Attendance attendance = new Attendance();
        attendance.setId(ATTENDANCE_ID);
        attendance.setProgramSubjectSectionEnrollment(enrollment);
        attendance.setClassDate(CLASS_DATE);
        attendance.setStatus(AttendanceStatus.ABSENT);
        when(attendanceFinder.findOrThrowOwnedBy(ATTENDANCE_ID, INSTRUCTOR_ID)).thenReturn(attendance);
        when(attendanceFinder.findOrThrow(ATTENDANCE_ID)).thenReturn(attendance);
        when(attendanceRepository.findById(ATTENDANCE_ID)).thenReturn(Optional.of(attendance));
        when(attendanceRepository.save(attendance)).thenReturn(attendance);
        return attendance;
    }

    private static MockMultipartFile file(String contentType) {
        return new MockMultipartFile("proof", "proof.bin", contentType, new byte[] {1, 2, 3});
    }

    @Test
    void teacherProofLeavesAttendancePending() {
        Attendance attendance = absence();
        when(fileStorageService.store(eq(STUDENT_ID), anyString(), any())).thenReturn("students/3/proof.pdf");

        AttendanceResponse response = recorderOn(LocalDate.of(2026, 3, 9))
                .recordProof(ATTENDANCE_ID, INSTRUCTOR_ID, "Certificado médico", file("application/pdf"));

        assertThat(response.status()).isEqualTo("JUSTIFICATION_PENDING");
        assertThat(attendance.getJustificationReason()).isEqualTo("Certificado médico");
        assertThat(attendance.getJustificationAttachmentUrl()).isEqualTo("students/3/proof.pdf");
        assertThat(attendance.getJustifiedBy()).isNull();
        assertThat(attendance.getJustifiedAt()).isNull();
    }

    @Test
    void blankReasonFallsBackToTheTeacherDefault() {
        Attendance attendance = absence();
        when(fileStorageService.store(eq(STUDENT_ID), anyString(), any())).thenReturn("students/3/proof.jpg");

        recorderOn(LocalDate.of(2026, 3, 9)).recordProof(ATTENDANCE_ID, INSTRUCTOR_ID, "  ", file("image/jpeg"));

        assertThat(attendance.getJustificationReason()).isEqualTo("Comprobante subido por el docente");
    }

    @Test
    void teacherCannotSubmitForOtherSectionAttendance() {
        when(attendanceFinder.findOrThrowOwnedBy(ATTENDANCE_ID, INSTRUCTOR_ID)).thenThrow(new AttendanceNotFoundException(ATTENDANCE_ID));

        assertThatThrownBy(() -> recorderOn(LocalDate.of(2026, 3, 9))
                .recordProof(ATTENDANCE_ID, INSTRUCTOR_ID, "x", file("application/pdf")))
                .isInstanceOf(AttendanceNotFoundException.class);
        verify(fileStorageService, never()).store(anyLong(), anyString(), any());
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void rejectsNonPdfOrJpeg() {
        absence();

        assertThatThrownBy(() -> recorderOn(LocalDate.of(2026, 3, 9))
                .recordProof(ATTENDANCE_ID, INSTRUCTOR_ID, "x", file("image/png")))
                .isInstanceOf(UnsupportedFileTypeException.class);
        verify(fileStorageService, never()).store(anyLong(), anyString(), any());
    }

    @Test
    void failsAfterDeadlineAndLeavesTheAbsenceUntouched() {
        Attendance attendance = absence();
        when(fileStorageService.store(eq(STUDENT_ID), anyString(), any())).thenReturn("students/3/proof.pdf");

        assertThatThrownBy(() -> recorderOn(LocalDate.of(2026, 3, 14))
                .recordProof(ATTENDANCE_ID, INSTRUCTOR_ID, "x", file("application/pdf")))
                .isInstanceOf(JustificationDeadlineExpiredException.class);
        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void refusesAnAttendanceThatIsNotAbsent() {
        Attendance attendance = absence();
        attendance.setStatus(AttendanceStatus.PRESENT);
        when(fileStorageService.store(eq(STUDENT_ID), anyString(), any())).thenReturn("students/3/proof.pdf");

        assertThatThrownBy(() -> recorderOn(LocalDate.of(2026, 3, 9))
                .recordProof(ATTENDANCE_ID, INSTRUCTOR_ID, "x", file("application/pdf")))
                .isInstanceOf(CannotJustifyException.class);
    }
}
