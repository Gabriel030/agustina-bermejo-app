package com.red_escuela.backend.architecture.fixture.attendance;

import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;

import java.util.List;

public class StrayAbsenceCountingFixture {

    private final AttendanceRepository attendanceRepository;

    public StrayAbsenceCountingFixture(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    public List<AttendanceStatus> countWithStatuses() {
        return AttendanceStatus.absenceStatuses();
    }

    public int countWithQuery(Long sectionId) {
        return attendanceRepository.countsByProgramSection(sectionId, List.of(), List.of(), AttendanceStatus.ABSENT).size();
    }

    public boolean countWithStatusFlag(AttendanceStatus status) {
        return status.countsAsAbsence();
    }
}
