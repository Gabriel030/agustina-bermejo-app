package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.entity.AttendanceCorrection;
import com.red_escuela.backend.attendance.repository.AttendanceCorrectionRepository;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class SectionAttendanceRecorder {

    private final AttendanceRepository attendanceRepository;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final AttendanceSameDayCorrectionPolicy sameDayCorrectionPolicy;
    private final AttendanceCorrectionRepository correctionRepository;
    private final Clock clock;

    public SectionAttendanceRecorder(AttendanceRepository attendanceRepository,
                                     SectionEnrollmentFinder sectionEnrollmentFinder,
                                     AttendanceSameDayCorrectionPolicy sameDayCorrectionPolicy,
                                     AttendanceCorrectionRepository correctionRepository,
                                     Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.sameDayCorrectionPolicy = sameDayCorrectionPolicy;
        this.correctionRepository = correctionRepository;
        this.clock = clock;
    }

    public Attendance recordProgramAttendance(Long enrollmentId, LocalDate classDate,
                                              AttendanceStatus status, Instructor recordedBy) {
        var enrollment = sectionEnrollmentFinder.findProgramEnrollment(enrollmentId);
        Attendance attendance = attendanceRepository
                .findByProgramSubjectSectionEnrollmentIdAndClassDate(enrollmentId, classDate)
                .orElseGet(Attendance::new);
        attendance.setProgramSubjectSectionEnrollment(enrollment);
        attendance.setClassDate(classDate);
        return saveRecordingOrCorrection(attendance, status, recordedBy);
    }

    public Attendance recordCourseAttendance(Long enrollmentId, LocalDate classDate,
                                             AttendanceStatus status, Instructor recordedBy) {
        var enrollment = sectionEnrollmentFinder.findCourseEnrollment(enrollmentId);
        Attendance attendance = attendanceRepository
                .findByCourseSubjectSectionEnrollmentIdAndClassDate(enrollmentId, classDate)
                .orElseGet(Attendance::new);
        attendance.setCourseSubjectSectionEnrollment(enrollment);
        attendance.setClassDate(classDate);
        return saveRecordingOrCorrection(attendance, status, recordedBy);
    }

    private Attendance saveRecordingOrCorrection(Attendance attendance, AttendanceStatus status, Instructor recordedBy) {
        boolean isSameDayCorrection = attendance.getStatus() != null
                && attendance.getStatus() != status
                && sameDayCorrectionPolicy.allowsCorrection(attendance, recordedBy);
        if (!isSameDayCorrection) {
            attendance.setRecordedBy(recordedBy);
            attendance.record(status);
            return attendanceRepository.save(attendance);
        }
        AttendanceStatus previous = attendance.correct(status);
        Attendance saved = attendanceRepository.save(attendance);
        registerCorrection(saved, previous, status, recordedBy);
        return saved;
    }

    private void registerCorrection(Attendance attendance, AttendanceStatus previous,
                                    AttendanceStatus corrected, Instructor correctedBy) {
        AttendanceCorrection correction = new AttendanceCorrection();
        correction.setAttendance(attendance);
        correction.setCorrectedBy(correctedBy);
        correction.setPreviousStatus(previous);
        correction.setCorrectedStatus(corrected);
        correction.setCorrectedAt(LocalDateTime.now(clock));
        correctionRepository.save(correction);
    }
}
