package com.red_escuela.backend.service;
import com.red_escuela.backend.identity.repository.PersonRepository;

import com.red_escuela.backend.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.dto.AttendanceRiskResponse;
import com.red_escuela.backend.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.dto.RosterEntryResponse;
import com.red_escuela.backend.entity.Attendance;
import com.red_escuela.backend.entity.AttendanceStatus;
import com.red_escuela.backend.entity.ClassSession;
import com.red_escuela.backend.entity.ClassSessionStatus;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.exception.AttendanceNotFoundException;
import com.red_escuela.backend.exception.CannotJustifyException;
import com.red_escuela.backend.exception.ClassSessionAlreadyCanceledException;
import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.exception.NotOwnSectionException;
import com.red_escuela.backend.exception.PersonNotFoundException;
import com.red_escuela.backend.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.repository.AttendanceRepository;
import com.red_escuela.backend.repository.ClassSessionRepository;
import com.red_escuela.backend.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RF-DO-03/04, RF-PR-08, RF-PR-12: toma de asistencia por clase (Docente, propia comisión),
 * justificación de inasistencias (Preceptor/Administrativo) y cálculo derivado de "libre por
 * inasistencias" (nunca persistido — decisión 5 de la spec técnica). Además: roster/historial/
 * reporte de ausencias de una comisión y el ciclo de vida de la "clase" (ClassSession) de una
 * fecha puntual (temario, observaciones, cancelación).
 */
@Service
public class AttendanceService {

    private static final List<EnrollmentStatus> ACTIVE_ENROLLMENT_STATUSES =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS);
    private static final List<AttendanceStatus> ABSENCE_STATUSES_FOR_COUNT =
            List.of(AttendanceStatus.ABSENT, AttendanceStatus.JUSTIFICATION_PENDING, AttendanceStatus.JUSTIFIED);
    private static final Set<AttendanceStatus> PRESENT_LIKE_STATUSES =
            Set.of(AttendanceStatus.PRESENT, AttendanceStatus.LATE, AttendanceStatus.JUSTIFIED);
    private static final List<AttendanceStatus> PRESENT_LIKE_STATUSES_LIST =
            List.copyOf(PRESENT_LIKE_STATUSES);

    private final AttendanceRepository attendanceRepository;
    private final ClassSessionRepository classSessionRepository;
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
            ClassSessionRepository classSessionRepository,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository,
            SubjectSectionRepository subjectSectionRepository,
            CourseSubjectSectionRepository courseSubjectSectionRepository,
            SubjectSectionInstructorRepository subjectSectionInstructorRepository,
            CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository,
            InstructorRepository instructorRepository,
            PersonRepository personRepository) {
        this.attendanceRepository = attendanceRepository;
        this.classSessionRepository = classSessionRepository;
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
        SubjectSection section = subjectSectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));
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
        upsertClassSessionProgram(section, request.classDate(), instructor, request.topic(), request.observations());
        return resultado;
    }

    @Transactional
    public List<Attendance> cargarAsistenciaCourseSection(Long sectionId, Long instructorId, CargarAsistenciaRequest request) {
        CourseSubjectSection section = courseSubjectSectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));
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
        upsertClassSessionCourse(section, request.classDate(), instructor, request.topic(), request.observations());
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

    @Transactional
    public Attendance subirComprobante(Long attendanceId, String reason, String attachmentUrl) {
        Attendance attendance = findOrThrow(attendanceId);
        if (attendance.getStatus() != AttendanceStatus.ABSENT) {
            throw new CannotJustifyException(
                    "La asistencia " + attendanceId + " no está ABSENT, no se puede justificar");
        }
        attendance.setStatus(AttendanceStatus.JUSTIFICATION_PENDING);
        attendance.setJustificationReason(reason);
        attendance.setJustificationAttachmentUrl(attachmentUrl);
        return attendanceRepository.save(attendance);
    }

    @Transactional
    public Attendance aprobarJustificacion(Long attendanceId) {
        Attendance attendance = findOrThrow(attendanceId);
        if (attendance.getStatus() != AttendanceStatus.JUSTIFICATION_PENDING) {
            throw new CannotJustifyException(
                    "La asistencia " + attendanceId + " no tiene una justificación pendiente");
        }
        Long personId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        Person justifiedBy = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));

        attendance.setStatus(AttendanceStatus.JUSTIFIED);
        attendance.setJustifiedBy(justifiedBy);
        attendance.setJustifiedAt(LocalDateTime.now());
        return attendanceRepository.save(attendance);
    }

    @Transactional
    public Attendance rechazarJustificacion(Long attendanceId) {
        Attendance attendance = findOrThrow(attendanceId);
        if (attendance.getStatus() != AttendanceStatus.JUSTIFICATION_PENDING) {
            throw new CannotJustifyException(
                    "La asistencia " + attendanceId + " no tiene una justificación pendiente");
        }
        attendance.setStatus(AttendanceStatus.ABSENT);
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
        long ausentes = attendanceRepository.countByProgramSubjectSectionEnrollmentIdAndStatusIn(
                enrollmentId, List.of(AttendanceStatus.ABSENT, AttendanceStatus.JUSTIFICATION_PENDING));
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
        long ausentes = attendanceRepository.countByCourseSubjectSectionEnrollmentIdAndStatusIn(
                enrollmentId, List.of(AttendanceStatus.ABSENT, AttendanceStatus.JUSTIFICATION_PENDING));
        return calcularRiesgo(total, ausentes, maxAbsencePercentage);
    }

    public List<RosterEntryResponse> obtenerRosterProgramSection(Long sectionId, Long instructorId) {
        if (!subjectSectionRepository.existsById(sectionId)) {
            throw new SectionNotFoundException(sectionId);
        }
        if (!subjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        return construirRosterProgram(sectionId);
    }

    public List<RosterEntryResponse> obtenerRosterCourseSection(Long sectionId, Long instructorId) {
        if (!courseSubjectSectionRepository.existsById(sectionId)) {
            throw new SectionNotFoundException(sectionId);
        }
        if (!courseSubjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        return construirRosterCourse(sectionId);
    }

    public List<RosterEntryResponse> reporteDeAusenciasProgramSection(Long sectionId, Long instructorIdIfDocente, Integer minAbsences) {
        if (!subjectSectionRepository.existsById(sectionId)) {
            throw new SectionNotFoundException(sectionId);
        }
        exigirPropiaSeccionSiEsDocente(instructorIdIfDocente, sectionId, () ->
                subjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorIdIfDocente));
        return ordenarPorAusencias(construirRosterProgram(sectionId), minAbsences);
    }

    public List<RosterEntryResponse> reporteDeAusenciasCourseSection(Long sectionId, Long instructorIdIfDocente, Integer minAbsences) {
        if (!courseSubjectSectionRepository.existsById(sectionId)) {
            throw new SectionNotFoundException(sectionId);
        }
        exigirPropiaSeccionSiEsDocente(instructorIdIfDocente, sectionId, () ->
                courseSubjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorIdIfDocente));
        return ordenarPorAusencias(construirRosterCourse(sectionId), minAbsences);
    }

    public AttendanceHistoryResponse obtenerHistorialProgramSection(Long sectionId, Long instructorIdIfDocente) {
        if (!subjectSectionRepository.existsById(sectionId)) {
            throw new SectionNotFoundException(sectionId);
        }
        exigirPropiaSeccionSiEsDocente(instructorIdIfDocente, sectionId, () ->
                subjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorIdIfDocente));

        Set<LocalDate> fechasCanceladas = classSessionRepository.findBySubjectSectionId(sectionId).stream()
                .filter(cs -> cs.getStatus() == ClassSessionStatus.CANCELED)
                .map(ClassSession::getClassDate)
                .collect(Collectors.toSet());
        List<Attendance> asistencias = attendanceRepository.findByProgramSubjectSectionEnrollment_SubjectSection_Id(sectionId)
                .stream().filter(a -> !fechasCanceladas.contains(a.getClassDate())).toList();
        Map<Long, List<Attendance>> porInscripcion = asistencias.stream()
                .collect(Collectors.groupingBy(a -> a.getProgramSubjectSectionEnrollment().getId()));

        List<SubjectSectionEnrollment> inscriptos =
                subjectSectionEnrollmentRepository.findBySubjectSectionIdAndStatusIn(sectionId, ACTIVE_ENROLLMENT_STATUSES);
        List<AttendanceHistoryResponse.StudentHistoryRow> filas = inscriptos.stream()
                .map(enrollment -> construirFilaHistorial(
                        enrollment.getStudent().getId(), enrollment.getStudent().getPerson(),
                        porInscripcion.getOrDefault(enrollment.getId(), List.of())))
                .sorted(Comparator.comparing(AttendanceHistoryResponse.StudentHistoryRow::studentName))
                .toList();
        List<LocalDate> fechas = asistencias.stream().map(Attendance::getClassDate).distinct().sorted().toList();
        return new AttendanceHistoryResponse(fechas, filas);
    }

    public AttendanceHistoryResponse obtenerHistorialCourseSection(Long sectionId, Long instructorIdIfDocente) {
        if (!courseSubjectSectionRepository.existsById(sectionId)) {
            throw new SectionNotFoundException(sectionId);
        }
        exigirPropiaSeccionSiEsDocente(instructorIdIfDocente, sectionId, () ->
                courseSubjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorIdIfDocente));

        Set<LocalDate> fechasCanceladas = classSessionRepository.findByCourseSubjectSectionId(sectionId).stream()
                .filter(cs -> cs.getStatus() == ClassSessionStatus.CANCELED)
                .map(ClassSession::getClassDate)
                .collect(Collectors.toSet());
        List<Attendance> asistencias = attendanceRepository.findByCourseSubjectSectionEnrollment_CourseSubjectSection_Id(sectionId)
                .stream().filter(a -> !fechasCanceladas.contains(a.getClassDate())).toList();
        Map<Long, List<Attendance>> porInscripcion = asistencias.stream()
                .collect(Collectors.groupingBy(a -> a.getCourseSubjectSectionEnrollment().getId()));

        List<CourseSubjectSectionEnrollment> inscriptos =
                courseSubjectSectionEnrollmentRepository.findByCourseSubjectSectionIdAndStatusIn(sectionId, ACTIVE_ENROLLMENT_STATUSES);
        List<AttendanceHistoryResponse.StudentHistoryRow> filas = inscriptos.stream()
                .map(enrollment -> construirFilaHistorial(
                        enrollment.getStudent().getId(), enrollment.getStudent().getPerson(),
                        porInscripcion.getOrDefault(enrollment.getId(), List.of())))
                .sorted(Comparator.comparing(AttendanceHistoryResponse.StudentHistoryRow::studentName))
                .toList();
        List<LocalDate> fechas = asistencias.stream().map(Attendance::getClassDate).distinct().sorted().toList();
        return new AttendanceHistoryResponse(fechas, filas);
    }

    public ClassSession obtenerClaseProgramSection(Long sectionId, Long instructorId, LocalDate classDate) {
        if (!subjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        return classSessionRepository.findBySubjectSectionIdAndClassDate(sectionId, classDate).orElse(null);
    }

    public ClassSession obtenerClaseCourseSection(Long sectionId, Long instructorId, LocalDate classDate) {
        if (!courseSubjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        return classSessionRepository.findByCourseSubjectSectionIdAndClassDate(sectionId, classDate).orElse(null);
    }

    @Transactional
    public void cancelarClaseProgramSection(Long sectionId, Long instructorId, LocalDate classDate) {
        SubjectSection section = subjectSectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));
        if (!subjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        Instructor instructor = resolveInstructor(instructorId);
        ClassSession session = classSessionRepository.findBySubjectSectionIdAndClassDate(sectionId, classDate)
                .orElseGet(() -> nuevaClaseProgram(section, classDate, instructor));
        if (session.getStatus() == ClassSessionStatus.CANCELED) {
            throw new ClassSessionAlreadyCanceledException(session.getId());
        }
        session.setStatus(ClassSessionStatus.CANCELED);
        classSessionRepository.save(session);
    }

    @Transactional
    public void cancelarClaseCourseSection(Long sectionId, Long instructorId, LocalDate classDate) {
        CourseSubjectSection section = courseSubjectSectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));
        if (!courseSubjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        Instructor instructor = resolveInstructor(instructorId);
        ClassSession session = classSessionRepository.findByCourseSubjectSectionIdAndClassDate(sectionId, classDate)
                .orElseGet(() -> nuevaClaseCourse(section, classDate, instructor));
        if (session.getStatus() == ClassSessionStatus.CANCELED) {
            throw new ClassSessionAlreadyCanceledException(session.getId());
        }
        session.setStatus(ClassSessionStatus.CANCELED);
        classSessionRepository.save(session);
    }

    private List<RosterEntryResponse> construirRosterProgram(Long sectionId) {
        List<SubjectSectionEnrollment> inscriptos =
                subjectSectionEnrollmentRepository.findBySubjectSectionIdAndStatusIn(sectionId, ACTIVE_ENROLLMENT_STATUSES);
        return inscriptos.stream().map(this::construirEntradaRosterProgram).toList();
    }

    private List<RosterEntryResponse> construirRosterCourse(Long sectionId) {
        List<CourseSubjectSectionEnrollment> inscriptos = courseSubjectSectionEnrollmentRepository
                .findByCourseSubjectSectionIdAndStatusIn(sectionId, ACTIVE_ENROLLMENT_STATUSES);
        return inscriptos.stream().map(this::construirEntradaRosterCourse).toList();
    }

    private RosterEntryResponse construirEntradaRosterProgram(SubjectSectionEnrollment enrollment) {
        Person person = enrollment.getStudent().getPerson();
        long total = attendanceRepository.countByProgramSubjectSectionEnrollmentId(enrollment.getId());
        long presentes = attendanceRepository.countByProgramSubjectSectionEnrollmentIdAndStatusIn(
                enrollment.getId(), PRESENT_LIKE_STATUSES_LIST);
        long absenceCount = attendanceRepository.countByProgramSubjectSectionEnrollmentIdAndStatusIn(
                enrollment.getId(), ABSENCE_STATUSES_FOR_COUNT);
        Double attendancePercentage = total == 0 ? null : (double) presentes / total;
        boolean atRisk = calcularRiesgoDeLibrePorProgramEnrollment(enrollment.getId()).libre();
        return new RosterEntryResponse(
                enrollment.getId(), enrollment.getStudent().getId(), nombreCompleto(person), person.getDni(),
                absenceCount, attendancePercentage, atRisk);
    }

    private RosterEntryResponse construirEntradaRosterCourse(CourseSubjectSectionEnrollment enrollment) {
        Person person = enrollment.getStudent().getPerson();
        long total = attendanceRepository.countByCourseSubjectSectionEnrollmentId(enrollment.getId());
        long presentes = attendanceRepository.countByCourseSubjectSectionEnrollmentIdAndStatusIn(
                enrollment.getId(), PRESENT_LIKE_STATUSES_LIST);
        long absenceCount = attendanceRepository.countByCourseSubjectSectionEnrollmentIdAndStatusIn(
                enrollment.getId(), ABSENCE_STATUSES_FOR_COUNT);
        Double attendancePercentage = total == 0 ? null : (double) presentes / total;
        boolean atRisk = calcularRiesgoDeLibrePorCourseEnrollment(enrollment.getId()).libre();
        return new RosterEntryResponse(
                enrollment.getId(), enrollment.getStudent().getId(), nombreCompleto(person), person.getDni(),
                absenceCount, attendancePercentage, atRisk);
    }

    private AttendanceHistoryResponse.StudentHistoryRow construirFilaHistorial(
            Long studentId, Person person, List<Attendance> asistenciasDelAlumno) {
        Map<LocalDate, AttendanceHistoryResponse.HistoryCell> celdasPorFecha = asistenciasDelAlumno.stream()
                .collect(Collectors.toMap(
                        Attendance::getClassDate,
                        a -> new AttendanceHistoryResponse.HistoryCell(a.getId(), a.getStatus().name()),
                        (a, b) -> b));
        long presentes = asistenciasDelAlumno.stream().filter(a -> PRESENT_LIKE_STATUSES.contains(a.getStatus())).count();
        Double porcentaje = asistenciasDelAlumno.isEmpty() ? null : (double) presentes / asistenciasDelAlumno.size();
        return new AttendanceHistoryResponse.StudentHistoryRow(studentId, nombreCompleto(person), celdasPorFecha, porcentaje);
    }

    private List<RosterEntryResponse> ordenarPorAusencias(List<RosterEntryResponse> roster, Integer minAbsences) {
        return roster.stream()
                .filter(r -> minAbsences == null || r.absenceCount() >= minAbsences)
                .sorted(Comparator.comparingLong(RosterEntryResponse::absenceCount).reversed())
                .toList();
    }

    private void exigirPropiaSeccionSiEsDocente(
            Long instructorIdIfDocente, Long sectionId, java.util.function.BooleanSupplier esPropia) {
        if (instructorIdIfDocente != null && !esPropia.getAsBoolean()) {
            throw new NotOwnSectionException(instructorIdIfDocente, sectionId);
        }
    }

    private void upsertClassSessionProgram(
            SubjectSection section, LocalDate classDate, Instructor instructor, String topic, String observations) {
        ClassSession session = classSessionRepository.findBySubjectSectionIdAndClassDate(section.getId(), classDate)
                .orElseGet(() -> nuevaClaseProgram(section, classDate, instructor));
        aplicarDatosDeClase(session, topic, observations);
        classSessionRepository.save(session);
    }

    private void upsertClassSessionCourse(
            CourseSubjectSection section, LocalDate classDate, Instructor instructor, String topic, String observations) {
        ClassSession session = classSessionRepository.findByCourseSubjectSectionIdAndClassDate(section.getId(), classDate)
                .orElseGet(() -> nuevaClaseCourse(section, classDate, instructor));
        aplicarDatosDeClase(session, topic, observations);
        classSessionRepository.save(session);
    }

    private void aplicarDatosDeClase(ClassSession session, String topic, String observations) {
        if (session.getStatus() != ClassSessionStatus.CANCELED) {
            session.setStatus(ClassSessionStatus.HELD);
        }
        if (topic != null) {
            session.setTopic(topic);
        }
        if (observations != null) {
            session.setObservations(observations);
        }
    }

    private ClassSession nuevaClaseProgram(SubjectSection section, LocalDate classDate, Instructor instructor) {
        ClassSession session = new ClassSession();
        session.setSubjectSection(section);
        session.setClassDate(classDate);
        session.setCreatedBy(instructor);
        session.setStatus(ClassSessionStatus.SCHEDULED);
        return session;
    }

    private ClassSession nuevaClaseCourse(CourseSubjectSection section, LocalDate classDate, Instructor instructor) {
        ClassSession session = new ClassSession();
        session.setCourseSubjectSection(section);
        session.setClassDate(classDate);
        session.setCreatedBy(instructor);
        session.setStatus(ClassSessionStatus.SCHEDULED);
        return session;
    }

    private String nombreCompleto(Person person) {
        return person.getName() + " " + person.getLastname();
    }

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
