package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;

public record LoadProgramSectionAttendanceCommand(Long sectionId, Long instructorId, CargarAsistenciaRequest request) {
}
