package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import org.springframework.stereotype.Service;

@Service
public class AttendanceFinder {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceSectionAccess sectionAccess;

    public AttendanceFinder(AttendanceRepository attendanceRepository, AttendanceSectionAccess sectionAccess) {
        this.attendanceRepository = attendanceRepository;
        this.sectionAccess = sectionAccess;
    }

    public Attendance findOrThrowOwnedBy(Long id, Long instructorIdIfDocente) {
        Attendance attendance = findOrThrow(id);
        sectionAccess.requireOwnSectionOfAttendanceIfInstructorGiven(attendance, instructorIdIfDocente);
        return attendance;
    }

    public Attendance findOrThrow(Long id) {
        return attendanceRepository.findById(id).orElseThrow(() -> new AttendanceNotFoundException(id));
    }
}
