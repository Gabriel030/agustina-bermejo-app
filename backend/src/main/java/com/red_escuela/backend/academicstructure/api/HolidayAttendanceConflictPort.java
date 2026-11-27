package com.red_escuela.backend.academicstructure.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface HolidayAttendanceConflictPort {

    List<LocalDate> findDatesWithRecordedAttendance(Collection<LocalDate> dates);
}
