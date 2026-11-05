package com.red_escuela.backend.academicrecord.usecase.studentrecord;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceResponse;
import org.springframework.web.multipart.MultipartFile;

public interface SubmitMyAbsenceProofUseCase {
    StudentAttendanceResponse submit(Long studentId, Long attendanceId, String reason, MultipartFile proof);
}
