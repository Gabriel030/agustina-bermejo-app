package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class StudentAttendanceReader {

    private final AttendanceRepository attendanceRepository;

    public StudentAttendanceReader(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    public List<StudentAttendanceView> listProgramEnrollmentAttendance(Long enrollmentId) {
        return attendanceRepository.findByProgramSubjectSectionEnrollmentId(enrollmentId).stream()
                .map(StudentAttendanceView::from)
                .toList();
    }

    public List<StudentAttendanceView> listCourseEnrollmentAttendance(Long enrollmentId) {
        return attendanceRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId).stream()
                .map(StudentAttendanceView::from)
                .toList();
    }

    public void requireAttendanceOwnedBy(Long attendanceId, Long studentId) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new AttendanceNotFoundException(attendanceId));
        Long ownerId = attendance.getProgramSubjectSectionEnrollment() != null
                ? attendance.getProgramSubjectSectionEnrollment().getStudent().getId()
                : attendance.getCourseSubjectSectionEnrollment().getStudent().getId();
        if (!ownerId.equals(studentId)) {
            throw new AttendanceNotFoundException(attendanceId);
        }
    }
}
