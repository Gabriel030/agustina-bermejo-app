package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;

public record LoadCourseSectionAttendanceCommand(Long sectionId, Long instructorId, CargarAsistenciaRequest request) {
}
