package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.exception.ClassOnHolidayException;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.service.AttendanceSectionAccess;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.attendance.service.ClassSessionUpserter;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@UseCase
public class LoadProgramSectionAttendanceService implements LoadProgramSectionAttendanceUseCase {

    private final AttendanceRepository attendanceRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final AttendanceSectionAccess sectionAccess;
    private final ClassSessionUpserter classSessionUpserter;
    private final HolidayRepository holidayRepository;
    private final ApplicationEventPublisher eventPublisher;

    public LoadProgramSectionAttendanceService(AttendanceRepository attendanceRepository,
                                                SubjectSectionRepository subjectSectionRepository,
                                                SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
                                                AttendanceSectionAccess sectionAccess,
                                                ClassSessionUpserter classSessionUpserter,
                                                HolidayRepository holidayRepository,
                                                ApplicationEventPublisher eventPublisher) {
        this.attendanceRepository = attendanceRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.sectionAccess = sectionAccess;
        this.classSessionUpserter = classSessionUpserter;
        this.holidayRepository = holidayRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public List<Attendance> load(LoadProgramSectionAttendanceCommand command) {
        SubjectSection section = subjectSectionRepository.findById(command.sectionId())
                .orElseThrow(() -> new SectionNotFoundException(command.sectionId()));
        sectionAccess.requireOwnProgramSection(command.sectionId(), command.instructorId());
        Instructor instructor = sectionAccess.resolveInstructor(command.instructorId());

        var request = command.request();
        if (holidayRepository.existsByDate(request.classDate())) {
            throw new ClassOnHolidayException(request.classDate());
        }
        Map<Long, AttendanceStatus> statusByEnrollment = new LinkedHashMap<>();
        for (var entry : request.entries()) {
            statusByEnrollment.put(entry.enrollmentId(), entry.status());
        }
        List<Attendance> procesadas = new ArrayList<>();
        for (var entry : statusByEnrollment.entrySet()) {
            SubjectSectionEnrollment enrollment = subjectSectionEnrollmentRepository.findById(entry.getKey())
                    .orElseThrow(() -> new SectionEnrollmentNotFoundException(entry.getKey()));
            Attendance attendance = attendanceRepository
                    .findByProgramSubjectSectionEnrollmentIdAndClassDate(entry.getKey(), request.classDate())
                    .orElseGet(Attendance::new);
            attendance.setProgramSubjectSectionEnrollment(enrollment);
            attendance.setClassDate(request.classDate());
            attendance.setRecordedBy(instructor);
            attendance.record(entry.getValue());
            procesadas.add(attendanceRepository.save(attendance));
        }
        classSessionUpserter.upsertHeldForProgramSection(section, request.classDate(), instructor, request.topic(), request.observations());
        procesadas.forEach(saved -> eventPublisher.publishEvent(AttendanceStatusChangedEvent.of(saved)));
        return procesadas;
    }
}
