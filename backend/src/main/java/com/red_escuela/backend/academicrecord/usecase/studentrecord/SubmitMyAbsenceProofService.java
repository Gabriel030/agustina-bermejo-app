package com.red_escuela.backend.academicrecord.usecase.studentrecord;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceResponse;
import com.red_escuela.backend.academicrecord.service.AcademicRecordAttendanceFinder;
import com.red_escuela.backend.academicrecord.service.AcademicRecordAttendanceProofRecorder;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.web.multipart.MultipartFile;

@UseCase
public class SubmitMyAbsenceProofService implements SubmitMyAbsenceProofUseCase {

    private final AcademicRecordAttendanceFinder attendanceFinder;
    private final AcademicRecordAttendanceProofRecorder proofRecorder;

    public SubmitMyAbsenceProofService(
            AcademicRecordAttendanceFinder attendanceFinder, AcademicRecordAttendanceProofRecorder proofRecorder) {
        this.attendanceFinder = attendanceFinder;
        this.proofRecorder = proofRecorder;
    }

    @Override
    public StudentAttendanceResponse submit(Long studentId, Long attendanceId, String reason, MultipartFile proof) {
        attendanceFinder.requireAttendanceOwnedBy(attendanceId, studentId);
        return proofRecorder.recordProof(studentId, attendanceId, reason, proof);
    }
}
