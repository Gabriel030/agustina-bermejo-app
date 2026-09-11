package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import org.springframework.stereotype.Service;

@Service
public class AttendanceFinder {

    private final AttendanceRepository attendanceRepository;

    public AttendanceFinder(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    public Attendance findOrThrow(Long id) {
        return attendanceRepository.findById(id).orElseThrow(() -> new AttendanceNotFoundException(id));
    }
}
