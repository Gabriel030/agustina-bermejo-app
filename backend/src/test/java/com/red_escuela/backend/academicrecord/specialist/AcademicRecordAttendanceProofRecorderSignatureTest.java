package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.attendance.api.AttendanceProofSubmitter;
import com.red_escuela.backend.attendance.api.JustificationDeadlinePolicyTest;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.shared.storage.FileStorageService;
import com.red_escuela.backend.shared.storage.UnsupportedFileTypeException;
import com.red_escuela.backend.shared.storage.UploadTestFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcademicRecordAttendanceProofRecorderSignatureTest {

    @TempDir
    Path uploads;

    private static final long ATTENDANCE_ID = 11L;

    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
    private Attendance attendance;
    private AcademicRecordAttendanceProofRecorder recorder;

    @BeforeEach
    void setUp() {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setId(7L);
        attendance = new Attendance();
        attendance.setProgramSubjectSectionEnrollment(enrollment);
        attendance.setId(ATTENDANCE_ID);
        attendance.setClassDate(LocalDate.of(2026, 3, 6));
        attendance.setStatus(AttendanceStatus.ABSENT);
        when(attendanceRepository.findById(ATTENDANCE_ID)).thenReturn(Optional.of(attendance));
        when(attendanceRepository.save(attendance)).thenReturn(attendance);
        AttendanceProofSubmitter submitter = new AttendanceProofSubmitter(
                attendanceRepository, JustificationDeadlinePolicyTest.policy(LocalDate.of(2026, 3, 9), 5));
        recorder = new AcademicRecordAttendanceProofRecorder(submitter, new FileStorageService(uploads.toString()));
    }

    @Test
    void renamedExecutableIsRejected() throws IOException {
        assertThatThrownBy(() -> recorder.recordProof(7L, ATTENDANCE_ID, "motivo", UploadTestFiles.renamedExecutable()))
                .isInstanceOf(UnsupportedFileTypeException.class);

        assertThat(UploadTestFiles.countFiles(uploads)).isZero();
        verify(attendanceRepository, never()).save(attendance);
    }

    @Test
    void declaredPdfWithOtherBytesIsRejected() throws IOException {
        assertThatThrownBy(() -> recorder.recordProof(7L, ATTENDANCE_ID, "motivo", UploadTestFiles.declaredPdfWithPngBytes()))
                .isInstanceOf(UnsupportedFileTypeException.class);

        assertThat(UploadTestFiles.countFiles(uploads)).isZero();
    }

    @Test
    void validFileStillStored() throws IOException {
        recorder.recordProof(7L, ATTENDANCE_ID, "motivo", UploadTestFiles.validPdf());

        assertThat(UploadTestFiles.countFiles(uploads)).isEqualTo(1);
        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.JUSTIFICATION_PENDING);
        assertThat(attendance.getJustificationAttachmentUrl()).startsWith("students/7/");
    }
}
