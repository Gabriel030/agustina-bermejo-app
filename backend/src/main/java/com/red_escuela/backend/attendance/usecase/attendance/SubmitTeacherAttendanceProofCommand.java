package com.red_escuela.backend.attendance.usecase.attendance;

import org.springframework.web.multipart.MultipartFile;

public record SubmitTeacherAttendanceProofCommand(Long attendanceId, Long instructorId, String reason, MultipartFile proof) {
}
