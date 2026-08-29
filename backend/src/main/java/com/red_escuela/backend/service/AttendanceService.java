package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.AttendanceRiskResponse;
import com.red_escuela.backend.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.entity.Attendance;
import com.red_escuela.backend.entity.AttendanceStatus;
import com.red_escuela.backend.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.entity.Instructor;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.exception.AttendanceNotFoundException;
import com.red_escuela.backend.exception.CannotJustifyException;
import com.red_escuela.backend.exception.InstructorNotFoundException;
import com.red_escuela.backend.exception.NotOwnSectionException;
import com.red_escuela.backend.exception.PersonNotFoundException;
import com.red_escuela.backend.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.exception.SectionNotFoundException;
import com.red_escuela.backend.repository.AttendanceRepository;
import com.red_escuela.backend.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.repository.InstructorRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.repository.SubjectSectionRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RF-DO-03/04, RF-PR-08, RF-PR-12: toma de asistencia por clase (Docente, propia comisión),
 * justificación de inasistencias (Preceptor/Administrativo) y cálculo derivado de "libre por
 * inasistencias" (nunca persistido — decisión 5 de la spec técnica).
 */
@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSubjectSectionRepository;
    private final SubjectSectionInstructorRepository subjectSectionInstructorRepository;
    private final CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository;
    private final InstructorRepository instructorRepository;
    private final PersonRepository personRepository;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository,
            SubjectSectionRepository subjectSectionRepository,
            CourseSubjectSectionRepository courseSubjectSectionRepository,
            SubjectSectionInstructorRepository subjectSectionInstructorRepository,
            CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository,
            InstructorRepository instructorRepository,
            PersonRepository personRepository) {
        this.attendanceRepository = attendanceRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSubjectSectionRepository = courseSubjectSectionRepository;
        this.subjectSectionInstructorRepository = subjectSectionInstructorRepository;
        this.courseSubjectSectionInstructorRepository = courseSubjectSectionInstructorRepository;
        this.instructorRepository = instructorRepository;
        this.personRepository = personRepository;
    }

    @Transactional
    public List<Attendance> cargarAsistenciaProgramSection(Long sectionId, Long instructorId, CargarAsistenciaRequest request) {
        if (!subjectSectionRepository.existsById(sectionId)) {
            throw new SectionNotFoundException(sectionId);
        }
        if (!subjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        Instructor instructor = resolveInstructor(instructorId);

        // Duplicado dentro del MISMO payload (no en la base): se procesa en memoria para que la
        // última entry de un enrollmentId repetido gane sobre una sola fila, en vez de depender
        // del timing de flush/lectura entre entries.
        Map<Long, Attendance> procesadas = new HashMap<>();
        List<Attendance> resultado = new ArrayList<>();
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
        resultado.addAll(procesadas.values());
        return resultado;
    }

    @Transactional
    public List<Attendance> cargarAsistenciaCourseSection(Long sectionId, Long instructorId, CargarAsistenciaRequest request) {
        if (!courseSubjectSectionRepository.existsById(sectionId)) {
            throw new SectionNotFoundException(sectionId);
        }
        if (!courseSubjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        Instructor instructor = resolveInstructor(instructorId);

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
        return new ArrayList<>(procesadas.values());
    }

    @Transactional
    public Attendance justificar(Long attendanceId, String reason, String attachmentUrl) {
        Attendance attendance = findOrThrow(attendanceId);
        if (attendance.getStatus() != AttendanceStatus.ABSENT) {
            throw new CannotJustifyException(
                    "La asistencia " + attendanceId + " no está ABSENT, no se puede justificar");
        }
        Long personId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        Person justifiedBy = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));

        attendance.setStatus(AttendanceStatus.JUSTIFIED);
        attendance.setJustificationReason(reason);
        attendance.setJustificationAttachmentUrl(attachmentUrl);
        attendance.setJustifiedBy(justifiedBy);
        attendance.setJustifiedAt(LocalDateTime.now());
        return attendanceRepository.save(attendance);
    }

    public Attendance verDetalle(Long id) {
        return findOrThrow(id);
    }

    public List<Attendance> listarPorProgramEnrollment(Long enrollmentId) {
        return attendanceRepository.findByProgramSubjectSectionEnrollmentId(enrollmentId);
    }

    public List<Attendance> listarPorCourseEnrollment(Long enrollmentId) {
        return attendanceRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId);
    }

    public AttendanceRiskResponse calcularRiesgoDeLibrePorProgramEnrollment(Long enrollmentId) {
        SubjectSectionEnrollment enrollment = subjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        Double maxAbsencePercentage = enrollment.getSubjectSection().getMaxAbsencePercentage();
        if (maxAbsencePercentage == null) {
            return new AttendanceRiskResponse(false, false, null, null);
        }
        long total = attendanceRepository.countByProgramSubjectSectionEnrollmentId(enrollmentId);
        long ausentes = attendanceRepository
                .countByProgramSubjectSectionEnrollmentIdAndStatus(enrollmentId, AttendanceStatus.ABSENT);
        return calcularRiesgo(total, ausentes, maxAbsencePercentage);
    }

    public AttendanceRiskResponse calcularRiesgoDeLibrePorCourseEnrollment(Long enrollmentId) {
        CourseSubjectSectionEnrollment enrollment = courseSubjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        Double maxAbsencePercentage = enrollment.getCourseSubjectSection().getMaxAbsencePercentage();
        if (maxAbsencePercentage == null) {
            return new AttendanceRiskResponse(false, false, null, null);
        }
        long total = attendanceRepository.countByCourseSubjectSectionEnrollmentId(enrollmentId);
        long ausentes = attendanceRepository
                .countByCourseSubjectSectionEnrollmentIdAndStatus(enrollmentId, AttendanceStatus.ABSENT);
        return calcularRiesgo(total, ausentes, maxAbsencePercentage);
    }

    // LATE cuenta como presente, JUSTIFIED no cuenta ni a favor ni en contra (decisión de
    // producto): solo ABSENT computa como ausente en el numerador del porcentaje.
    private AttendanceRiskResponse calcularRiesgo(long total, long ausentes, double maxAbsencePercentage) {
        if (total == 0) {
            return new AttendanceRiskResponse(true, false, 0.0, maxAbsencePercentage);
        }
        double porcentaje = (double) ausentes / total;
        return new AttendanceRiskResponse(true, porcentaje > maxAbsencePercentage, porcentaje, maxAbsencePercentage);
    }

    private Instructor resolveInstructor(Long instructorId) {
        return instructorRepository.findById(instructorId)
                .orElseThrow(() -> new InstructorNotFoundException(instructorId));
    }

    private Attendance findOrThrow(Long id) {
        return attendanceRepository.findById(id).orElseThrow(() -> new AttendanceNotFoundException(id));
    }
}
