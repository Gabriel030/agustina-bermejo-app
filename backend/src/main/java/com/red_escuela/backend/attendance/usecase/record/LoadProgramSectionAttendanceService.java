package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.specialist.LoadProgramSectionAttendanceTransaction;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class LoadProgramSectionAttendanceService implements LoadProgramSectionAttendanceUseCase {

    private final LoadProgramSectionAttendanceTransaction transaction;

    public LoadProgramSectionAttendanceService(LoadProgramSectionAttendanceTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public List<Attendance> load(LoadProgramSectionAttendanceCommand command) {
        return transaction.load(command.sectionId(), command.instructorId(), command.request());
    }
}
