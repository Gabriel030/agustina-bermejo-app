package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.attendance.exception.ClassOnHolidayException;
import com.red_escuela.backend.attendance.exception.ClassSessionCanceledException;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SectionAttendanceLoader {

    private final SectionAttendanceRecorder attendanceRecorder;
    private final AttendanceSectionAccessChecker sectionAccess;
    private final ClassSessionUpserter classSessionUpserter;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final ClassSessionRepository classSessionRepository;

    public SectionAttendanceLoader(SectionAttendanceRecorder attendanceRecorder,
                                   AttendanceSectionAccessChecker sectionAccess,
                                   ClassSessionUpserter classSessionUpserter,
                                   SectionEnrollmentFinder sectionEnrollmentFinder,
                                   ClassSessionRepository classSessionRepository) {
        this.attendanceRecorder = attendanceRecorder;
        this.sectionAccess = sectionAccess;
        this.classSessionUpserter = classSessionUpserter;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.classSessionRepository = classSessionRepository;
    }

    @Transactional
    public List<Attendance> loadCourseSectionAttendance(Long sectionId, Long instructorId, CargarAsistenciaRequest request) {
        CourseSubjectSection section = sectionAccess.findCourseSection(sectionId);
        sectionAccess.requireOwnCourseSection(sectionId, instructorId);
        Instructor instructor = sectionAccess.resolveInstructor(instructorId);
        requireNotHoliday(request);
        requireCourseSessionNotCanceled(sectionId, request.classDate());
        Map<Long, AttendanceStatus> statuses = statusByEnrollment(request);
        sectionEnrollmentFinder.requireActiveInCourseSection(sectionId, statuses.keySet());
        List<Attendance> processed = new ArrayList<>();
        for (var entry : statuses.entrySet()) {
            processed.add(attendanceRecorder.recordCourseAttendance(
                    entry.getKey(), request.classDate(), entry.getValue(), instructor));
        }
        classSessionUpserter.upsertHeldForCourseSection(section, request.classDate(), instructor, request.topic(), request.observations());
        return processed;
    }

    @Transactional
    public List<Attendance> loadProgramSectionAttendance(Long sectionId, Long instructorId, CargarAsistenciaRequest request) {
        SubjectSection section = sectionAccess.findProgramSection(sectionId);
        sectionAccess.requireOwnProgramSection(sectionId, instructorId);
        Instructor instructor = sectionAccess.resolveInstructor(instructorId);
        requireNotHoliday(request);
        requireProgramSessionNotCanceled(sectionId, request.classDate());
        Map<Long, AttendanceStatus> statuses = statusByEnrollment(request);
        sectionEnrollmentFinder.requireActiveInProgramSection(sectionId, statuses.keySet());
        List<Attendance> processed = new ArrayList<>();
        for (var entry : statuses.entrySet()) {
            processed.add(attendanceRecorder.recordProgramAttendance(
                    entry.getKey(), request.classDate(), entry.getValue(), instructor));
        }
        classSessionUpserter.upsertHeldForProgramSection(section, request.classDate(), instructor, request.topic(), request.observations());
        return processed;
    }

    private void requireNotHoliday(CargarAsistenciaRequest request) {
        if (sectionAccess.isHoliday(request.classDate())) {
            throw new ClassOnHolidayException(request.classDate());
        }
    }

    private void requireCourseSessionNotCanceled(Long sectionId, LocalDate classDate) {
        classSessionRepository.findByCourseSubjectSectionIdAndClassDate(sectionId, classDate)
                .filter(session -> session.getStatus() == ClassSessionStatus.CANCELED)
                .ifPresent(session -> {
                    throw new ClassSessionCanceledException(classDate);
                });
    }

    private void requireProgramSessionNotCanceled(Long sectionId, LocalDate classDate) {
        classSessionRepository.findBySubjectSectionIdAndClassDate(sectionId, classDate)
                .filter(session -> session.getStatus() == ClassSessionStatus.CANCELED)
                .ifPresent(session -> {
                    throw new ClassSessionCanceledException(classDate);
                });
    }

    private Map<Long, AttendanceStatus> statusByEnrollment(CargarAsistenciaRequest request) {
        Map<Long, AttendanceStatus> statusByEnrollment = new LinkedHashMap<>();
        for (var entry : request.entries()) {
            statusByEnrollment.put(entry.enrollmentId(), entry.status());
        }
        return statusByEnrollment;
    }
}
