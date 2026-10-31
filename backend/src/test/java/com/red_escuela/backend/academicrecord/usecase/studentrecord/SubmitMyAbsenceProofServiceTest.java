package com.red_escuela.backend.academicrecord.usecase.studentrecord;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceResponse;
import com.red_escuela.backend.academicrecord.service.AcademicRecordAttendanceFinder;
import com.red_escuela.backend.academicrecord.service.AcademicRecordAttendanceProofRecorder;
import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SubmitMyAbsenceProofServiceTest {

    private final AcademicRecordAttendanceFinder attendanceFinder = mock(AcademicRecordAttendanceFinder.class);
    private final AcademicRecordAttendanceProofRecorder proofRecorder = mock(AcademicRecordAttendanceProofRecorder.class);
    private final SubmitMyAbsenceProofService service = new SubmitMyAbsenceProofService(attendanceFinder, proofRecorder);
    private final MultipartFile proof = mock(MultipartFile.class);

    @Test
    void recordsTheProofAfterCheckingTheAttendanceBelongsToTheStudent() {
        StudentAttendanceResponse response = new StudentAttendanceResponse(
                5L, 9L, LocalDate.of(2025, 9, 15), "ABSENT", "Fiebre", "/proofs/5", LocalDateTime.of(2025, 9, 16, 10, 0));
        when(proofRecorder.recordProof(2L, 5L, "Fiebre", proof)).thenReturn(response);

        assertThat(service.submit(2L, 5L, "Fiebre", proof)).isSameAs(response);

        var order = inOrder(attendanceFinder, proofRecorder);
        order.verify(attendanceFinder).requireAttendanceOwnedBy(5L, 2L);
        order.verify(proofRecorder).recordProof(2L, 5L, "Fiebre", proof);
    }

    @Test
    void rejectsAnAttendanceThatBelongsToAnotherStudentWithoutRecordingTheProof() {
        doThrow(new AttendanceNotFoundException(5L)).when(attendanceFinder).requireAttendanceOwnedBy(5L, 77L);

        assertThatThrownBy(() -> service.submit(77L, 5L, "Fiebre", proof)).isInstanceOf(AttendanceNotFoundException.class);

        verifyNoInteractions(proofRecorder);
    }

    @Test
    void propagatesTheRecorderFailureAfterTheOwnershipCheck() {
        when(proofRecorder.recordProof(any(), any(), any(), any())).thenThrow(new IllegalStateException("cannot justify"));

        assertThatThrownBy(() -> service.submit(2L, 5L, "Fiebre", proof)).isInstanceOf(IllegalStateException.class);
    }
}
