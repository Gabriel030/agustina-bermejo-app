package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.exception.ClassOnHolidayException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SectionAttendanceLoader {

    private final SectionAttendanceRecorder attendanceRecorder;
    private final AttendanceSectionAccess sectionAccess;
    private final ClassSessionUpserter classSessionUpserter;

    public SectionAttendanceLoader(SectionAttendanceRecorder attendanceRecorder,
                                   AttendanceSectionAccess sectionAccess,
                                   ClassSessionUpserter classSessionUpserter) {
        this.attendanceRecorder = attendanceRecorder;
        this.sectionAccess = sectionAccess;
        this.classSessionUpserter = classSessionUpserter;
    }

    @Transactional
    public List<Attendance> loadCourseSectionAttendance(Long sectionId, Long instructorId, CargarAsistenciaRequest request) {
        CourseSubjectSection section = sectionAccess.findCourseSection(sectionId);
        sectionAccess.requireOwnCourseSection(sectionId, instructorId);
        Instructor instructor = sectionAccess.resolveInstructor(instructorId);
        requireNotHoliday(request);
        List<Attendance> processed = new ArrayList<>();
        for (var entry : statusByEnrollment(request).entrySet()) {
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
        List<Attendance> processed = new ArrayList<>();
        for (var entry : statusByEnrollment(request).entrySet()) {
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

    private Map<Long, AttendanceStatus> statusByEnrollment(CargarAsistenciaRequest request) {
        Map<Long, AttendanceStatus> statusByEnrollment = new LinkedHashMap<>();
        for (var entry : request.entries()) {
            statusByEnrollment.put(entry.enrollmentId(), entry.status());
        }
        return statusByEnrollment;
    }
}
