package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.RosterEntryResponse;

import java.util.List;

public interface GetProgramSectionAbsenceReportUseCase {
    List<RosterEntryResponse> get(GetProgramSectionAbsenceReportCommand command);
}
