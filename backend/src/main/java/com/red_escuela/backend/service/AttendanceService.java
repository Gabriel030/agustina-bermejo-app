package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.dto.AttendanceRiskResponse;
import com.red_escuela.backend.dto.LoadAttendanceRequest;
import com.red_escuela.backend.dto.RosterEntryResponse;
import com.red_escuela.backend.entity.Attendance;
import com.red_escuela.backend.entity.AttendanceStatus;
import com.red_escuela.backend.entity.ClassSession;
import com.red_escuela.backend.entity.ClassSessionStatus;
import com.red_escuela.backend.entity.CourseSubjectSection;
import com.red_escuela.backend.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.Instructor;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.SubjectSection;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.exception.AttendanceNotFoundException;
import com.red_escuela.backend.exception.CannotJustifyException;
import com.red_escuela.backend.exception.ClassSessionAlreadyCanceledException;
import com.red_escuela.backend.exception.InstructorNotFoundException;
import com.red_escuela.backend.exception.NotOwnSectionException;
import com.red_escuela.backend.exception.PersonNotFoundException;
import com.red_escuela.backend.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.exception.SectionNotFoundException;
import com.red_escuela.backend.repository.AttendanceRepository;
import com.red_escuela.backend.repository.ClassSessionRepository;
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
    public List<Attendance> loadAttendanceProgramSection(Long sectionId, Long instructorId, LoadAttendanceRequest request) {
        SubjectSection section = subjectSectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));
        if (!subjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        Instructor instructor = resolveInstructor(instructorId);

        // Duplicado dentro del MISMO payload (no en la base): se procesa en memoria para que la
        // última entry de un enrollmentId repetido gane sobre una sola fila, en vez de depender
        // del timing de flush/lectura entre entries.
        Map<Long, Attendance> processed = new HashMap<>();
        List<Attendance> result = new ArrayList<>();
        for (var entry : request.entries()) {
            Attendance attendance = processed.get(entry.enrollmentId());
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
            processed.put(entry.enrollmentId(), attendance);
        }
        result.addAll(processed.values());
        upsertClassSessionProgram(section, request.classDate(), instructor, request.topic(), request.observations());
        return result;
    }

    @Transactional
    public List<Attendance> loadAttendanceCourseSection(Long sectionId, Long instructorId, LoadAttendanceRequest request) {
        CourseSubjectSection section = courseSubjectSectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));
        if (!courseSubjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        Instructor instructor = resolveInstructor(instructorId);

        Map<Long, Attendance> processed = new HashMap<>();
        for (var entry : request.entries()) {
            Attendance attendance = processed.get(entry.enrollmentId());
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
            processed.put(entry.enrollmentId(), attendance);
        }
        upsertClassSessionCourse(section, request.classDate(), instructor, request.topic(), request.observations());
        return new ArrayList<>(processed.values());
    }

    @Transactional
    public Attendance justify(Long attendanceId, String reason, String attachmentUrl) {
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
    public Attendance uploadJustificationProof(Long attendanceId, String reason, String attachmentUrl) {
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
    public Attendance approveJustification(Long attendanceId) {
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
    public Attendance rejectJustification(Long attendanceId) {
        Attendance attendance = findOrThrow(attendanceId);
        if (attendance.getStatus() != AttendanceStatus.JUSTIFICATION_PENDING) {
            throw new CannotJustifyException(
                    "La asistencia " + attendanceId + " no tiene una justificación pendiente");
        }
        attendance.setStatus(AttendanceStatus.ABSENT);
        return attendanceRepository.save(attendance);
    }

    public Attendance viewDetail(Long id) {
        return findOrThrow(id);
    }

    public List<Attendance> listByProgramEnrollment(Long enrollmentId) {
        return attendanceRepository.findByProgramSubjectSectionEnrollmentId(enrollmentId);
    }

    public List<Attendance> listByCourseEnrollment(Long enrollmentId) {
        return attendanceRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId);
    }

    public AttendanceRiskResponse calculateDropoutRiskByProgramEnrollment(Long enrollmentId) {
        SubjectSectionEnrollment enrollment = subjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        Double maxAbsencePercentage = enrollment.getSubjectSection().getMaxAbsencePercentage();
        if (maxAbsencePercentage == null) {
            return new AttendanceRiskResponse(false, false, null, null);
        }
        long total = attendanceRepository.countByProgramSubjectSectionEnrollmentId(enrollmentId);
        long absentCount = attendanceRepository.countByProgramSubjectSectionEnrollmentIdAndStatusIn(
                enrollmentId, List.of(AttendanceStatus.ABSENT, AttendanceStatus.JUSTIFICATION_PENDING));
        return calculateRisk(total, absentCount, maxAbsencePercentage);
    }

    public AttendanceRiskResponse calculateDropoutRiskByCourseEnrollment(Long enrollmentId) {
        CourseSubjectSectionEnrollment enrollment = courseSubjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        Double maxAbsencePercentage = enrollment.getCourseSubjectSection().getMaxAbsencePercentage();
        if (maxAbsencePercentage == null) {
            return new AttendanceRiskResponse(false, false, null, null);
        }
        long total = attendanceRepository.countByCourseSubjectSectionEnrollmentId(enrollmentId);
        long absentCount = attendanceRepository.countByCourseSubjectSectionEnrollmentIdAndStatusIn(
                enrollmentId, List.of(AttendanceStatus.ABSENT, AttendanceStatus.JUSTIFICATION_PENDING));
        return calculateRisk(total, absentCount, maxAbsencePercentage);
    }

    public List<RosterEntryResponse> getRosterProgramSection(Long sectionId, Long instructorId) {
        if (!subjectSectionRepository.existsById(sectionId)) {
            throw new SectionNotFoundException(sectionId);
        }
        if (!subjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        return buildRosterProgram(sectionId);
    }

    public List<RosterEntryResponse> getRosterCourseSection(Long sectionId, Long instructorId) {
        if (!courseSubjectSectionRepository.existsById(sectionId)) {
            throw new SectionNotFoundException(sectionId);
        }
        if (!courseSubjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        return buildRosterCourse(sectionId);
    }

    public List<RosterEntryResponse> absenceReportProgramSection(Long sectionId, Long instructorIdIfTeacher, Integer minAbsences) {
        if (!subjectSectionRepository.existsById(sectionId)) {
            throw new SectionNotFoundException(sectionId);
        }
        exigirPropiaSeccionSiEsDocente(instructorIdIfTeacher, sectionId, () ->
                subjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorIdIfTeacher));
        return sortByAbsences(buildRosterProgram(sectionId), minAbsences);
    }

    public List<RosterEntryResponse> absenceReportCourseSection(Long sectionId, Long instructorIdIfTeacher, Integer minAbsences) {
        if (!courseSubjectSectionRepository.existsById(sectionId)) {
            throw new SectionNotFoundException(sectionId);
        }
        exigirPropiaSeccionSiEsDocente(instructorIdIfTeacher, sectionId, () ->
                courseSubjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorIdIfTeacher));
        return sortByAbsences(buildRosterCourse(sectionId), minAbsences);
    }

    public AttendanceHistoryResponse getHistoryProgramSection(Long sectionId, Long instructorIdIfTeacher) {
        if (!subjectSectionRepository.existsById(sectionId)) {
            throw new SectionNotFoundException(sectionId);
        }
        exigirPropiaSeccionSiEsDocente(instructorIdIfTeacher, sectionId, () ->
                subjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorIdIfTeacher));

        Set<LocalDate> canceledDates = classSessionRepository.findBySubjectSectionId(sectionId).stream()
                .filter(cs -> cs.getStatus() == ClassSessionStatus.CANCELED)
                .map(ClassSession::getClassDate)
                .collect(Collectors.toSet());
        List<Attendance> attendances = attendanceRepository.findByProgramSubjectSectionEnrollment_SubjectSection_Id(sectionId)
                .stream().filter(a -> !canceledDates.contains(a.getClassDate())).toList();
        Map<Long, List<Attendance>> byEnrollment = attendances.stream()
                .collect(Collectors.groupingBy(a -> a.getProgramSubjectSectionEnrollment().getId()));

        List<SubjectSectionEnrollment> enrolledStudents =
                subjectSectionEnrollmentRepository.findBySubjectSectionIdAndStatusIn(sectionId, ACTIVE_ENROLLMENT_STATUSES);
        List<AttendanceHistoryResponse.StudentHistoryRow> rows = enrolledStudents.stream()
                .map(enrollment -> construirFilaHistorial(
                        enrollment.getStudent().getId(), enrollment.getStudent().getPerson(),
                        byEnrollment.getOrDefault(enrollment.getId(), List.of())))
                .sorted(Comparator.comparing(AttendanceHistoryResponse.StudentHistoryRow::studentName))
                .toList();
        List<LocalDate> dates = attendances.stream().map(Attendance::getClassDate).distinct().sorted().toList();
        return new AttendanceHistoryResponse(dates, rows);
    }

    public AttendanceHistoryResponse getHistoryCourseSection(Long sectionId, Long instructorIdIfTeacher) {
        if (!courseSubjectSectionRepository.existsById(sectionId)) {
            throw new SectionNotFoundException(sectionId);
        }
        exigirPropiaSeccionSiEsDocente(instructorIdIfTeacher, sectionId, () ->
                courseSubjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorIdIfTeacher));

        Set<LocalDate> canceledDates = classSessionRepository.findByCourseSubjectSectionId(sectionId).stream()
                .filter(cs -> cs.getStatus() == ClassSessionStatus.CANCELED)
                .map(ClassSession::getClassDate)
                .collect(Collectors.toSet());
        List<Attendance> attendances = attendanceRepository.findByCourseSubjectSectionEnrollment_CourseSubjectSection_Id(sectionId)
                .stream().filter(a -> !canceledDates.contains(a.getClassDate())).toList();
        Map<Long, List<Attendance>> byEnrollment = attendances.stream()
                .collect(Collectors.groupingBy(a -> a.getCourseSubjectSectionEnrollment().getId()));

        List<CourseSubjectSectionEnrollment> enrolledStudents =
                courseSubjectSectionEnrollmentRepository.findByCourseSubjectSectionIdAndStatusIn(sectionId, ACTIVE_ENROLLMENT_STATUSES);
        List<AttendanceHistoryResponse.StudentHistoryRow> rows = enrolledStudents.stream()
                .map(enrollment -> construirFilaHistorial(
                        enrollment.getStudent().getId(), enrollment.getStudent().getPerson(),
                        byEnrollment.getOrDefault(enrollment.getId(), List.of())))
                .sorted(Comparator.comparing(AttendanceHistoryResponse.StudentHistoryRow::studentName))
                .toList();
        List<LocalDate> dates = attendances.stream().map(Attendance::getClassDate).distinct().sorted().toList();
        return new AttendanceHistoryResponse(dates, rows);
    }

    public ClassSession getClassSessionProgramSection(Long sectionId, Long instructorId, LocalDate classDate) {
        if (!subjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        return classSessionRepository.findBySubjectSectionIdAndClassDate(sectionId, classDate).orElse(null);
    }

    public ClassSession getClassSessionCourseSection(Long sectionId, Long instructorId, LocalDate classDate) {
        if (!courseSubjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        return classSessionRepository.findByCourseSubjectSectionIdAndClassDate(sectionId, classDate).orElse(null);
    }

    @Transactional
    public void cancelClassForProgramSection(Long sectionId, Long instructorId, LocalDate classDate) {
        SubjectSection section = subjectSectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));
        if (!subjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        Instructor instructor = resolveInstructor(instructorId);
        ClassSession session = classSessionRepository.findBySubjectSectionIdAndClassDate(sectionId, classDate)
                .orElseGet(() -> newClassSessionProgram(section, classDate, instructor));
        if (session.getStatus() == ClassSessionStatus.CANCELED) {
            throw new ClassSessionAlreadyCanceledException(session.getId());
        }
        session.setStatus(ClassSessionStatus.CANCELED);
        classSessionRepository.save(session);
    }

    @Transactional
    public void cancelClassForCourseSection(Long sectionId, Long instructorId, LocalDate classDate) {
        CourseSubjectSection section = courseSubjectSectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));
        if (!courseSubjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        Instructor instructor = resolveInstructor(instructorId);
        ClassSession session = classSessionRepository.findByCourseSubjectSectionIdAndClassDate(sectionId, classDate)
                .orElseGet(() -> newClassSessionCourse(section, classDate, instructor));
        if (session.getStatus() == ClassSessionStatus.CANCELED) {
            throw new ClassSessionAlreadyCanceledException(session.getId());
        }
        session.setStatus(ClassSessionStatus.CANCELED);
        classSessionRepository.save(session);
    }

    private List<RosterEntryResponse> buildRosterProgram(Long sectionId) {
        List<SubjectSectionEnrollment> enrolledStudents =
                subjectSectionEnrollmentRepository.findBySubjectSectionIdAndStatusIn(sectionId, ACTIVE_ENROLLMENT_STATUSES);
        return enrolledStudents.stream().map(this::buildRosterEntryProgram).toList();
    }

    private List<RosterEntryResponse> buildRosterCourse(Long sectionId) {
        List<CourseSubjectSectionEnrollment> enrolledStudents = courseSubjectSectionEnrollmentRepository
                .findByCourseSubjectSectionIdAndStatusIn(sectionId, ACTIVE_ENROLLMENT_STATUSES);
        return enrolledStudents.stream().map(this::buildRosterEntryCourse).toList();
    }

    private RosterEntryResponse buildRosterEntryProgram(SubjectSectionEnrollment enrollment) {
        Person person = enrollment.getStudent().getPerson();
        long total = attendanceRepository.countByProgramSubjectSectionEnrollmentId(enrollment.getId());
        long presentCount = attendanceRepository.countByProgramSubjectSectionEnrollmentIdAndStatusIn(
                enrollment.getId(), PRESENT_LIKE_STATUSES_LIST);
        long absenceCount = attendanceRepository.countByProgramSubjectSectionEnrollmentIdAndStatusIn(
                enrollment.getId(), ABSENCE_STATUSES_FOR_COUNT);
        Double attendancePercentage = total == 0 ? null : (double) presentCount / total;
        boolean atRisk = calculateDropoutRiskByProgramEnrollment(enrollment.getId()).libre();
        return new RosterEntryResponse(
                enrollment.getId(), enrollment.getStudent().getId(), fullName(person), person.getDni(),
                absenceCount, attendancePercentage, atRisk);
    }

    private RosterEntryResponse buildRosterEntryCourse(CourseSubjectSectionEnrollment enrollment) {
        Person person = enrollment.getStudent().getPerson();
        long total = attendanceRepository.countByCourseSubjectSectionEnrollmentId(enrollment.getId());
        long presentCount = attendanceRepository.countByCourseSubjectSectionEnrollmentIdAndStatusIn(
                enrollment.getId(), PRESENT_LIKE_STATUSES_LIST);
        long absenceCount = attendanceRepository.countByCourseSubjectSectionEnrollmentIdAndStatusIn(
                enrollment.getId(), ABSENCE_STATUSES_FOR_COUNT);
        Double attendancePercentage = total == 0 ? null : (double) presentCount / total;
        boolean atRisk = calculateDropoutRiskByCourseEnrollment(enrollment.getId()).libre();
        return new RosterEntryResponse(
                enrollment.getId(), enrollment.getStudent().getId(), fullName(person), person.getDni(),
                absenceCount, attendancePercentage, atRisk);
    }

    private AttendanceHistoryResponse.StudentHistoryRow construirFilaHistorial(
            Long studentId, Person person, List<Attendance> studentAttendances) {
        Map<LocalDate, AttendanceHistoryResponse.HistoryCell> cellsByDate = studentAttendances.stream()
                .collect(Collectors.toMap(
                        Attendance::getClassDate,
                        a -> new AttendanceHistoryResponse.HistoryCell(a.getId(), a.getStatus().name()),
                        (a, b) -> b));
        long presentCount = studentAttendances.stream().filter(a -> PRESENT_LIKE_STATUSES.contains(a.getStatus())).count();
        Double percentage = studentAttendances.isEmpty() ? null : (double) presentCount / studentAttendances.size();
        return new AttendanceHistoryResponse.StudentHistoryRow(studentId, fullName(person), cellsByDate, percentage);
    }

    private List<RosterEntryResponse> sortByAbsences(List<RosterEntryResponse> roster, Integer minAbsences) {
        return roster.stream()
                .filter(r -> minAbsences == null || r.absenceCount() >= minAbsences)
                .sorted(Comparator.comparingLong(RosterEntryResponse::absenceCount).reversed())
                .toList();
    }

    private void exigirPropiaSeccionSiEsDocente(
            Long instructorIdIfTeacher, Long sectionId, java.util.function.BooleanSupplier isOwnSection) {
        if (instructorIdIfTeacher != null && !isOwnSection.getAsBoolean()) {
            throw new NotOwnSectionException(instructorIdIfTeacher, sectionId);
        }
    }

    private void upsertClassSessionProgram(
            SubjectSection section, LocalDate classDate, Instructor instructor, String topic, String observations) {
        ClassSession session = classSessionRepository.findBySubjectSectionIdAndClassDate(section.getId(), classDate)
                .orElseGet(() -> newClassSessionProgram(section, classDate, instructor));
        applyClassData(session, topic, observations);
        classSessionRepository.save(session);
    }

    private void upsertClassSessionCourse(
            CourseSubjectSection section, LocalDate classDate, Instructor instructor, String topic, String observations) {
        ClassSession session = classSessionRepository.findByCourseSubjectSectionIdAndClassDate(section.getId(), classDate)
                .orElseGet(() -> newClassSessionCourse(section, classDate, instructor));
        applyClassData(session, topic, observations);
        classSessionRepository.save(session);
    }

    private void applyClassData(ClassSession session, String topic, String observations) {
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

    private ClassSession newClassSessionProgram(SubjectSection section, LocalDate classDate, Instructor instructor) {
        ClassSession session = new ClassSession();
        session.setSubjectSection(section);
        session.setClassDate(classDate);
        session.setCreatedBy(instructor);
        session.setStatus(ClassSessionStatus.SCHEDULED);
        return session;
    }

    private ClassSession newClassSessionCourse(CourseSubjectSection section, LocalDate classDate, Instructor instructor) {
        ClassSession session = new ClassSession();
        session.setCourseSubjectSection(section);
        session.setClassDate(classDate);
        session.setCreatedBy(instructor);
        session.setStatus(ClassSessionStatus.SCHEDULED);
        return session;
    }

    private String fullName(Person person) {
        return person.getName() + " " + person.getLastname();
    }

    private AttendanceRiskResponse calculateRisk(long total, long absentCount, double maxAbsencePercentage) {
        if (total == 0) {
            return new AttendanceRiskResponse(true, false, 0.0, maxAbsencePercentage);
        }
        double percentage = (double) absentCount / total;
        return new AttendanceRiskResponse(true, percentage > maxAbsencePercentage, percentage, maxAbsencePercentage);
    }

    private Instructor resolveInstructor(Long instructorId) {
        return instructorRepository.findById(instructorId)
                .orElseThrow(() -> new InstructorNotFoundException(instructorId));
    }

    private Attendance findOrThrow(Long id) {
        return attendanceRepository.findById(id).orElseThrow(() -> new AttendanceNotFoundException(id));
    }
}
