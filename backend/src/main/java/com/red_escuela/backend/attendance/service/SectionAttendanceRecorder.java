package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class SectionAttendanceRecorder {

    private final AttendanceRepository attendanceRepository;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;

    public SectionAttendanceRecorder(AttendanceRepository attendanceRepository,
                                     SectionEnrollmentFinder sectionEnrollmentFinder) {
        this.attendanceRepository = attendanceRepository;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
    }

    public Attendance recordProgramAttendance(Long enrollmentId, LocalDate classDate,
                                              AttendanceStatus status, Instructor recordedBy) {
        var enrollment = sectionEnrollmentFinder.findProgramEnrollment(enrollmentId);
        Attendance attendance = attendanceRepository
                .findByProgramSubjectSectionEnrollmentIdAndClassDate(enrollmentId, classDate)
                .orElseGet(Attendance::new);
        attendance.setProgramSubjectSectionEnrollment(enrollment);
        attendance.setClassDate(classDate);
        attendance.setRecordedBy(recordedBy);
        attendance.record(status);
        return attendanceRepository.save(attendance);
    }

    public Attendance recordCourseAttendance(Long enrollmentId, LocalDate classDate,
                                             AttendanceStatus status, Instructor recordedBy) {
        var enrollment = sectionEnrollmentFinder.findCourseEnrollment(enrollmentId);
        Attendance attendance = attendanceRepository
                .findByCourseSubjectSectionEnrollmentIdAndClassDate(enrollmentId, classDate)
                .orElseGet(Attendance::new);
        attendance.setCourseSubjectSectionEnrollment(enrollment);
        attendance.setClassDate(classDate);
        attendance.setRecordedBy(recordedBy);
        attendance.record(status);
        return attendanceRepository.save(attendance);
    }
}
