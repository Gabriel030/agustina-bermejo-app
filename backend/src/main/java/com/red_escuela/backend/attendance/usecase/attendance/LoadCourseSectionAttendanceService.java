package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.exception.ClassOnHolidayException;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.service.AttendanceSectionAccess;
import com.red_escuela.backend.attendance.service.SectionAttendanceRecorder;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.attendance.service.ClassSessionUpserter;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@UseCase
public class LoadCourseSectionAttendanceService implements LoadCourseSectionAttendanceUseCase {

    private final SectionAttendanceRecorder attendanceRecorder;
    private final AttendanceSectionAccess sectionAccess;
    private final ClassSessionUpserter classSessionUpserter;
    private final ApplicationEventPublisher eventPublisher;

    public LoadCourseSectionAttendanceService(SectionAttendanceRecorder attendanceRecorder,
                                               AttendanceSectionAccess sectionAccess,
                                               ClassSessionUpserter classSessionUpserter,
                                               ApplicationEventPublisher eventPublisher) {
        this.attendanceRecorder = attendanceRecorder;
        this.sectionAccess = sectionAccess;
        this.classSessionUpserter = classSessionUpserter;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public List<Attendance> load(LoadCourseSectionAttendanceCommand command) {
        CourseSubjectSection section = sectionAccess.findCourseSection(command.sectionId());
        sectionAccess.requireOwnCourseSection(command.sectionId(), command.instructorId());
        Instructor instructor = sectionAccess.resolveInstructor(command.instructorId());

        var request = command.request();
        if (sectionAccess.isHoliday(request.classDate())) {
            throw new ClassOnHolidayException(request.classDate());
        }
        Map<Long, AttendanceStatus> statusByEnrollment = new LinkedHashMap<>();
        for (var entry : request.entries()) {
            statusByEnrollment.put(entry.enrollmentId(), entry.status());
        }
        List<Attendance> procesadas = new ArrayList<>();
        for (var entry : statusByEnrollment.entrySet()) {
            procesadas.add(attendanceRecorder.recordCourseAttendance(
                    entry.getKey(), request.classDate(), entry.getValue(), instructor));
        }
        classSessionUpserter.upsertHeldForCourseSection(section, request.classDate(), instructor, request.topic(), request.observations());
        procesadas.forEach(saved -> eventPublisher.publishEvent(AttendanceStatusChangedEvent.of(saved)));
        return procesadas;
    }
}
