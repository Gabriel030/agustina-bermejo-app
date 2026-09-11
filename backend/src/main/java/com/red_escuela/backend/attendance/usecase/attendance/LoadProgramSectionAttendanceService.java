package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.service.AttendanceSectionAccess;
import com.red_escuela.backend.attendance.service.ClassSessionUpserter;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UseCase
public class LoadProgramSectionAttendanceService implements LoadProgramSectionAttendanceUseCase {

    private final AttendanceRepository attendanceRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final AttendanceSectionAccess sectionAccess;
    private final ClassSessionUpserter classSessionUpserter;

    public LoadProgramSectionAttendanceService(AttendanceRepository attendanceRepository,
                                                SubjectSectionRepository subjectSectionRepository,
                                                SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
                                                AttendanceSectionAccess sectionAccess,
                                                ClassSessionUpserter classSessionUpserter) {
        this.attendanceRepository = attendanceRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.sectionAccess = sectionAccess;
        this.classSessionUpserter = classSessionUpserter;
    }

    @Override
    @Transactional
    public List<Attendance> load(LoadProgramSectionAttendanceCommand command) {
        SubjectSection section = subjectSectionRepository.findById(command.sectionId())
                .orElseThrow(() -> new SectionNotFoundException(command.sectionId()));
        sectionAccess.requireOwnProgramSection(command.sectionId(), command.instructorId());
        Instructor instructor = sectionAccess.resolveInstructor(command.instructorId());

        var request = command.request();
        Map<Long, Attendance> procesadas = new HashMap<>();
        for (var entry : request.entries()) {
            Attendance attendance = procesadas.get(entry.enrollmentId());
            if (attendance == null) {
                SubjectSectionEnrollment enrollment = subjectSectionEnrollmentRepository.findById(entry.enrollmentId())
                        .orElseThrow(() -> new SectionEnrollmentNotFoundException(entry.enrollmentId()));
                attendance = attendanceRepository
                        .findByProgramSubjectSectionEnrollmentIdAndClassDate(entry.enrollmentId(), request.classDate())
                        .orElseGet(Attendance::new);
                attendance.setProgramSubjectSectionEnrollment(enrollment);
                attendance.setClassDate(request.classDate());
                attendance.setRecordedBy(instructor);
            }
            attendance.setStatus(entry.status());
            attendance = attendanceRepository.save(attendance);
            procesadas.put(entry.enrollmentId(), attendance);
        }
        classSessionUpserter.upsertHeldForProgramSection(section, request.classDate(), instructor, request.topic(), request.observations());
        return new ArrayList<>(procesadas.values());
    }
}
