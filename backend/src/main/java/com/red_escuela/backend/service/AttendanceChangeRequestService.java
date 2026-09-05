package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateAttendanceChangeRequestRequest;
import com.red_escuela.backend.entity.Attendance;
import com.red_escuela.backend.entity.AttendanceChangeRequest;
import com.red_escuela.backend.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.entity.Instructor;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.exception.AttendanceChangeRequestNotFoundException;
import com.red_escuela.backend.exception.AttendanceNotFoundException;
import com.red_escuela.backend.exception.InvalidAttendanceChangeRequestStateException;
import com.red_escuela.backend.exception.NotOwnSectionException;
import com.red_escuela.backend.exception.PersonNotFoundException;
import com.red_escuela.backend.notification.AttendanceChangeRequestApprovedEvent;
import com.red_escuela.backend.notification.AttendanceChangeRequestRejectedEvent;
import com.red_escuela.backend.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.repository.AttendanceRepository;
import com.red_escuela.backend.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.SubjectSectionInstructorRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/*
 * Workflow de modificación de una asistencia ya cargada (ej. Ausente -> Presente). A diferencia
 * de AttendanceService.justificar (que solo agrega contexto a un Ausente puntual), esto permite
 * pedir cualquier cambio de estado, pero nunca impacta directo: siempre pasa por aprobación de
 * Preceptor/Administrativo, mismos roles que ya aprueban justificaciones.
 */
@Service
public class AttendanceChangeRequestService {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final SubjectSectionInstructorRepository subjectSectionInstructorRepository;
    private final CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository;
    private final PersonRepository personRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AttendanceChangeRequestService(
            AttendanceChangeRequestRepository attendanceChangeRequestRepository,
            AttendanceRepository attendanceRepository,
            SubjectSectionInstructorRepository subjectSectionInstructorRepository,
            CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository,
            PersonRepository personRepository,
            ApplicationEventPublisher eventPublisher) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
        this.attendanceRepository = attendanceRepository;
        this.subjectSectionInstructorRepository = subjectSectionInstructorRepository;
        this.courseSubjectSectionInstructorRepository = courseSubjectSectionInstructorRepository;
        this.personRepository = personRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AttendanceChangeRequest create(Long attendanceId, Instructor requestedBy, CreateAttendanceChangeRequestRequest body) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new AttendanceNotFoundException(attendanceId));
        Long sectionId = resolveSectionId(attendance);
        boolean esProgramSection = attendance.getProgramSubjectSectionEnrollment() != null;
        boolean esPropia = esProgramSection
                ? subjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, requestedBy.getId())
                : courseSubjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, requestedBy.getId());
        if (!esPropia) {
            throw new NotOwnSectionException(requestedBy.getId(), sectionId);
        }

        AttendanceChangeRequest request = new AttendanceChangeRequest();
        request.setAttendance(attendance);
        request.setRequestedBy(requestedBy);
        request.setPreviousStatus(attendance.getStatus());
        request.setRequestedStatus(body.requestedStatus());
        request.setReason(body.reason());
        request.setStatus(AttendanceChangeRequestStatus.PENDING);
        return attendanceChangeRequestRepository.save(request);
    }

    public List<AttendanceChangeRequest> listByStatus(AttendanceChangeRequestStatus status) {
        return attendanceChangeRequestRepository.findByStatus(status);
    }

    public List<AttendanceChangeRequest> listOwn(Long instructorId) {
        return attendanceChangeRequestRepository.findByRequestedByIdOrderByCreatedAtDesc(instructorId);
    }

    @Transactional
    public AttendanceChangeRequest approve(Long id) {
        AttendanceChangeRequest request = findOrThrow(id);
        requirePending(request);

        Attendance attendance = request.getAttendance();
        attendance.setStatus(request.getRequestedStatus());
        attendanceRepository.save(attendance);

        request.setStatus(AttendanceChangeRequestStatus.APPROVED);
        request.setApprovedBy(resolveApprover());
        request.setApprovedAt(LocalDateTime.now());
        AttendanceChangeRequest saved = attendanceChangeRequestRepository.save(request);

        eventPublisher.publishEvent(new AttendanceChangeRequestApprovedEvent(saved, saved.getRequestedBy().getPerson()));
        return saved;
    }

    @Transactional
    public AttendanceChangeRequest reject(Long id) {
        AttendanceChangeRequest request = findOrThrow(id);
        requirePending(request);

        request.setStatus(AttendanceChangeRequestStatus.REJECTED);
        request.setApprovedBy(resolveApprover());
        request.setApprovedAt(LocalDateTime.now());
        AttendanceChangeRequest saved = attendanceChangeRequestRepository.save(request);

        eventPublisher.publishEvent(new AttendanceChangeRequestRejectedEvent(saved, saved.getRequestedBy().getPerson()));
        return saved;
    }

    private Long resolveSectionId(Attendance attendance) {
        if (attendance.getProgramSubjectSectionEnrollment() != null) {
            SubjectSectionEnrollment enrollment = attendance.getProgramSubjectSectionEnrollment();
            return enrollment.getSubjectSection().getId();
        }
        CourseSubjectSectionEnrollment enrollment = attendance.getCourseSubjectSectionEnrollment();
        return enrollment.getCourseSubjectSection().getId();
    }

    private void requirePending(AttendanceChangeRequest request) {
        if (request.getStatus() != AttendanceChangeRequestStatus.PENDING) {
            throw new InvalidAttendanceChangeRequestStateException(request.getId());
        }
    }

    private Person resolveApprover() {
        Long personId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        return personRepository.findById(personId).orElseThrow(() -> new PersonNotFoundException(personId));
    }

    private AttendanceChangeRequest findOrThrow(Long id) {
        return attendanceChangeRequestRepository.findById(id)
                .orElseThrow(() -> new AttendanceChangeRequestNotFoundException(id));
    }
}
