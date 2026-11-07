package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.dto.AttendanceResponse;
import com.red_escuela.backend.attendance.specialist.TeacherAttendanceProofRecorder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SubmitTeacherAttendanceProofService implements SubmitTeacherAttendanceProofUseCase {

    private final TeacherAttendanceProofRecorder proofRecorder;

    public SubmitTeacherAttendanceProofService(TeacherAttendanceProofRecorder proofRecorder) {
        this.proofRecorder = proofRecorder;
    }

    @Override
    public AttendanceResponse submit(SubmitTeacherAttendanceProofCommand command) {
        return proofRecorder.recordProof(
                command.attendanceId(), command.instructorId(), command.reason(), command.proof());
    }
}
