package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.service.AttendanceSectionAccess;
import com.red_escuela.backend.attendance.service.ClassSessionUpserter;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UseCase
public class LoadCourseSectionAttendanceService implements LoadCourseSectionAttendanceUseCase {

    private final AttendanceRepository attendanceRepository;
    private final CourseSubjectSectionRepository courseSubjectSectionRepository;
    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;
    private final AttendanceSectionAccess sectionAccess;
    private final ClassSessionUpserter classSessionUpserter;

    public LoadCourseSectionAttendanceService(AttendanceRepository attendanceRepository,
                                               CourseSubjectSectionRepository courseSubjectSectionRepository,
                                               CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository,
                                               AttendanceSectionAccess sectionAccess,
                                               ClassSessionUpserter classSessionUpserter) {
        this.attendanceRepository = attendanceRepository;
        this.courseSubjectSectionRepository = courseSubjectSectionRepository;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
        this.sectionAccess = sectionAccess;
        this.classSessionUpserter = classSessionUpserter;
    }

    @Override
    @Transactional
    public List<Attendance> load(LoadCourseSectionAttendanceCommand command) {
        CourseSubjectSection section = courseSubjectSectionRepository.findById(command.sectionId())
                .orElseThrow(() -> new SectionNotFoundException(command.sectionId()));
        sectionAccess.requireOwnCourseSection(command.sectionId(), command.instructorId());
        Instructor instructor = sectionAccess.resolveInstructor(command.instructorId());

        var request = command.request();
        Map<Long, Attendance> procesadas = new HashMap<>();
        for (var entry : request.entries()) {
            Attendance attendance = procesadas.get(entry.enrollmentId());
            if (attendance == null) {
                CourseSubjectSectionEnrollment enrollment = courseSubjectSectionEnrollmentRepository
                        .findById(entry.enrollmentId())
                        .orElseThrow(() -> new SectionEnrollmentNotFoundException(entry.enrollmentId()));
                attendance = attendanceRepository
                        .findByCourseSubjectSectionEnrollmentIdAndClassDate(entry.enrollmentId(), request.classDate())
                        .orElseGet(Attendance::new);
                attendance.setCourseSubjectSectionEnrollment(enrollment);
                attendance.setClassDate(request.classDate());
                attendance.setRecordedBy(instructor);
            }
            attendance.setStatus(entry.status());
            attendance = attendanceRepository.save(attendance);
            procesadas.put(entry.enrollmentId(), attendance);
        }
        classSessionUpserter.upsertHeldForCourseSection(section, request.classDate(), instructor, request.topic(), request.observations());
        return new ArrayList<>(procesadas.values());
    }
}
