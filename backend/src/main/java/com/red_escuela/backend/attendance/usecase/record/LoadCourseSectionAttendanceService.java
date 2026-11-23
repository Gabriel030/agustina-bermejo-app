package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.specialist.LoadCourseSectionAttendanceTransaction;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class LoadCourseSectionAttendanceService implements LoadCourseSectionAttendanceUseCase {

    private final LoadCourseSectionAttendanceTransaction transaction;

    public LoadCourseSectionAttendanceService(LoadCourseSectionAttendanceTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public List<Attendance> load(LoadCourseSectionAttendanceCommand command) {
        return transaction.load(command.sectionId(), command.instructorId(), command.request());
    }
}
