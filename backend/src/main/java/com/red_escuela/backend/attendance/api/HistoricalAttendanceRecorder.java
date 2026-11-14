package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.academicstructure.api.HistoricalSectionRecorder;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.attendance.exception.HistoricalAttendanceReferenceMissingException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import com.red_escuela.backend.enrollment.api.HistoricalEnrollmentRecorder;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.shared.domain.DocumentType;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HistoricalAttendanceRecorder {
    private final AttendanceRepository attendanceRepository;
    private final ClassSessionRepository classSessionRepository;
    private final HistoricalEnrollmentRecorder enrollmentRecorder;
    private final HistoricalSectionRecorder sectionRecorder;
    private final EntityManager entityManager;

    public HistoricalAttendanceRecorder(AttendanceRepository attendanceRepository,
            ClassSessionRepository classSessionRepository, HistoricalEnrollmentRecorder enrollmentRecorder,
            HistoricalSectionRecorder sectionRecorder, EntityManager entityManager) {
        this.attendanceRepository = attendanceRepository;
        this.classSessionRepository = classSessionRepository;
        this.enrollmentRecorder = enrollmentRecorder;
        this.sectionRecorder = sectionRecorder;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public boolean attendanceExists(DocumentType type, String number, Long sectionId, LocalDate classDate) {
        return enrollmentRecorder.findSectionEnrollmentId(type, number, sectionId)
                .flatMap(enrollmentId -> attendanceRepository
                        .findByProgramSubjectSectionEnrollmentIdAndClassDate(enrollmentId, classDate))
                .isPresent();
    }

    @Transactional
    public Long recordAttendance(HistoricalAttendance historical) {
        Long sectionId = historical.sectionId();
        Long enrollmentId = enrollmentRecorder
                .findSectionEnrollmentId(historical.documentType(), historical.documentNumber(), sectionId)
                .orElseThrow(() -> new HistoricalAttendanceReferenceMissingException(sectionId));
        Instructor instructor = entityManager.getReference(Instructor.class, sectionRecorder
                .findLeadInstructorId(sectionId).orElseThrow(() -> new HistoricalAttendanceReferenceMissingException(sectionId)));
        if (classSessionRepository.findBySubjectSectionIdAndClassDate(sectionId, historical.classDate()).isEmpty()) {
            ClassSession session = new ClassSession();
            session.setSubjectSection(entityManager.getReference(SubjectSection.class, sectionId));
            session.setClassDate(historical.classDate());
            session.setStatus(ClassSessionStatus.HELD);
            session.setCreatedBy(instructor);
            classSessionRepository.save(session);
        }
        Attendance attendance = new Attendance();
        attendance.setProgramSubjectSectionEnrollment(entityManager.getReference(SubjectSectionEnrollment.class, enrollmentId));
        attendance.setClassDate(historical.classDate());
        attendance.setStatus(AttendanceStatus.valueOf(historical.status()));
        attendance.setRecordedBy(instructor);
        return attendanceRepository.save(attendance).getId();
    }

    @Transactional
    public void discardAttendance(Long attendanceId) {
        attendanceRepository.findById(attendanceId).ifPresent(attendance -> {
            Long sectionId = attendance.getProgramSubjectSectionEnrollment().getSubjectSection().getId();
            LocalDate classDate = attendance.getClassDate();
            attendanceRepository.delete(attendance);
            attendanceRepository.flush();
            if (!attendanceRepository.existsByProgramSubjectSectionEnrollment_SubjectSection_IdAndClassDate(sectionId, classDate)) {
                classSessionRepository.findBySubjectSectionIdAndClassDate(sectionId, classDate)
                        .filter(session -> session.getStatus() == ClassSessionStatus.HELD)
                        .ifPresent(session -> {
                            classSessionRepository.delete(session);
                            classSessionRepository.flush();
                        });
            }
        });
    }
}
