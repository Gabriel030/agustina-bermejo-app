package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.academicstructure.api.HolidayAttendanceConflictPort;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Component
@Transactional(readOnly = true)
public class HolidayAttendanceConflictPortAdapter implements HolidayAttendanceConflictPort {

    private final AttendanceRepository attendanceRepository;

    public HolidayAttendanceConflictPortAdapter(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    @Override
    public List<LocalDate> findDatesWithRecordedAttendance(Collection<LocalDate> dates) {
        if (dates.isEmpty()) {
            return List.of();
        }
        return attendanceRepository.findDistinctClassDatesIn(dates);
    }
}
