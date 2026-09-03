package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CourseProgressResponse;
import com.red_escuela.backend.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.CourseSubject;
import com.red_escuela.backend.entity.CourseSubjectSection;
import com.red_escuela.backend.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.exception.AdmissionWindowClosedException;
import com.red_escuela.backend.exception.AlreadyEnrolledInCourseException;
import com.red_escuela.backend.exception.AmbiguousSectionException;
import com.red_escuela.backend.exception.CannotWithdrawException;
import com.red_escuela.backend.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.exception.CourseNotFoundException;
import com.red_escuela.backend.exception.IncompleteSectionSetupException;
import com.red_escuela.backend.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.exception.SectionNotFoundException;
import com.red_escuela.backend.exception.StudentNotActiveException;
import com.red_escuela.backend.exception.StudentNotConfirmedException;
import com.red_escuela.backend.exception.StudentNotFoundException;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.CourseSubjectRepository;
import com.red_escuela.backend.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * RF-PR-02: alta/baja de matriculación a Course (curso corto), exclusiva de Preceptor/
 * Administrativo (decisión 12). El alta inscribe automáticamente al alumno en TODOS
 * los módulos del curso (decisión 15) — todo o nada, misma transacción.
 */
@Service
public class CourseEnrollmentService {

    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final CourseSubjectRepository courseSubjectRepository;
    private final CourseSubjectSectionRepository courseSubjectSectionRepository;
    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;
    private final PersonRoleGranter personRoleGranter;

    public CourseEnrollmentService(
            CourseEnrollmentRepository courseEnrollmentRepository,
            StudentRepository studentRepository,
            CourseRepository courseRepository,
            CourseSubjectRepository courseSubjectRepository,
            CourseSubjectSectionRepository courseSubjectSectionRepository,
            CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository,
            PersonRoleGranter personRoleGranter) {
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.courseSubjectRepository = courseSubjectRepository;
        this.courseSubjectSectionRepository = courseSubjectSectionRepository;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
        this.personRoleGranter = personRoleGranter;
    }

    // Épica 7: variante para MeEnrollmentController (autoservicio). No se agrega el
    // otorgamiento de rol a matricular() en sí porque ese método también lo usa el
    // alta directa por staff (CourseEnrollmentController), que no debe otorgar
    // ALUMNO automáticamente — mismo criterio que ProgramEnrollmentApplicationService.aprobar
    // vs. ProgramEnrollmentService.matricular. Una sola transacción: si el controller
    // llamara a matricular() y al granter por separado, la mutación del rol quedaría
    // fuera de la transacción de matricular() y nunca se flushearía (bug real, hallado
    // en prueba manual — el commit de matricular() ya cerró la persistencia antes de
    // que el controller intentara agregar el rol).
    @Transactional
    public CourseEnrollment matricularComoAutoservicio(Long studentId, CreateCourseEnrollmentRequest request) {
        CourseEnrollment enrollment = matricular(studentId, request);
        personRoleGranter.otorgarAlumnoSiNoLoTiene(enrollment.getStudent().getPerson());
        return enrollment;
    }

    @Transactional
    public CourseEnrollment matricular(Long studentId, CreateCourseEnrollmentRequest request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new CourseNotFoundException(request.courseId()));
        validarPuedeMatricular(student, course);
        LocalDateTime now = LocalDateTime.now();

        // Resolver TODAS las comisiones antes de crear nada: si algún módulo falla (sin
        // comisión / ambiguo), no debe quedar ninguna fila creada (todo o nada, decisión 15).
        Map<CourseSubject, CourseSubjectSection> resueltas = new LinkedHashMap<>();
        for (CourseSubject courseSubject : courseSubjectRepository.findByCourseIdAndStatusOrderBySortOrder(course.getId(), StatusType.ACTIVE)) {
            resueltas.put(courseSubject, resolverSeccion(courseSubject, course, request));
        }

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(now);
        CourseEnrollment savedEnrollment = courseEnrollmentRepository.save(enrollment);

        for (CourseSubjectSection elegida : resueltas.values()) {
            courseSubjectSectionEnrollmentRepository.save(
                    crearOReactivarSubInscripcion(student, savedEnrollment, elegida, now));
        }

        return savedEnrollment;
    }

    // Extraído para reusar desde CourseEnrollmentApplicationService.solicitarConPago
    // (Épica 7 Fase 3): la solicitud con pago tiene que fallar con la misma validación
    // que matricular(), antes de generar Invoice/PaymentReceipt — no tiene sentido
    // pedirle un comprobante a alguien que igual no puede matricularse.
    void validarPuedeMatricular(Student student, Course course) {
        validarStudentHabilitado(student);

        if (course.getRegistrationStart() != null && course.getRegistrationEnd() != null
                && course.getRegistrationStart().isAfter(course.getRegistrationEnd())) {
            throw new InvalidWindowConfigurationException(
                    "El curso " + course.getId() + " tiene la ventana de inscripción mal configurada "
                            + "(registrationStart posterior a registrationEnd)");
        }
        LocalDateTime now = LocalDateTime.now();
        if (course.getRegistrationStart() != null && now.isBefore(course.getRegistrationStart())
                || course.getRegistrationEnd() != null && now.isAfter(course.getRegistrationEnd())) {
            throw new AdmissionWindowClosedException(
                    "Fuera de la ventana de inscripción del curso " + course.getId());
        }

        // CourseEnrollment.status usa EnrollmentStatus (sin PAUSED): la única activa posible es ENROLLED.
        List<CourseEnrollment> activas = courseEnrollmentRepository.findByStudentIdAndCourseIdAndStatusIn(
                student.getId(), course.getId(), List.of(EnrollmentStatus.ENROLLED));
        if (!activas.isEmpty()) {
            throw new AlreadyEnrolledInCourseException(student.getId(), course.getId());
        }
    }

    private CourseSubjectSection resolverSeccion(CourseSubject courseSubject, Course course, CreateCourseEnrollmentRequest request) {
        List<CourseSubjectSection> secciones = courseSubjectSectionRepository
                .findByCourseSubjectIdAndStatus(courseSubject.getId(), StatusType.ACTIVE)
                .stream()
                .filter(section -> section.getTerm().getId().equals(course.getTerm().getId()))
                .toList();

        if (secciones.isEmpty()) {
            throw new IncompleteSectionSetupException(courseSubject.getId());
        }
        if (secciones.size() == 1) {
            return secciones.get(0);
        }

        Long elegidaId = request.sectionOverrides() == null ? null : request.sectionOverrides().get(courseSubject.getId());
        if (elegidaId == null) {
            throw new AmbiguousSectionException(courseSubject.getId());
        }
        return secciones.stream()
                .filter(section -> section.getId().equals(elegidaId))
                .findFirst()
                .orElseThrow(() -> new AmbiguousSectionException(courseSubject.getId()));
    }

    private CourseSubjectSectionEnrollment crearOReactivarSubInscripcion(
            Student student, CourseEnrollment enrollment, CourseSubjectSection elegida, LocalDateTime now) {
        Optional<CourseSubjectSectionEnrollment> existente = courseSubjectSectionEnrollmentRepository
                .findByStudentIdAndCourseSubjectSectionId(student.getId(), elegida.getId());
        if (existente.isPresent() && existente.get().getStatus() == EnrollmentStatus.WITHDRAWN) {
            // El unique constraint (student_id, course_section_id) no admite un INSERT nuevo
            // para la misma comisión — reactivar la fila existente en vez de crear otra
            // (mismo criterio que SubjectSectionEnrollmentService, decisión 7 por analogía).
            CourseSubjectSectionEnrollment reactivada = existente.get();
            reactivada.setCourseEnrollment(enrollment);
            reactivada.setStatus(calcularStatusPorCupo(elegida));
            reactivada.setDroppedAt(null);
            return reactivada;
        }

        CourseSubjectSectionEnrollment nueva = new CourseSubjectSectionEnrollment();
        nueva.setStudent(student);
        nueva.setCourseEnrollment(enrollment);
        nueva.setCourseSubjectSection(elegida);
        nueva.setStatus(calcularStatusPorCupo(elegida));
        nueva.setEnrolledAt(now);
        nueva.setAttemptNumber(1);
        return nueva;
    }

    private EnrollmentStatus calcularStatusPorCupo(CourseSubjectSection section) {
        // Lock de fila: serializa altas/bajas/promociones concurrentes sobre la MISMA
        // comisión — sin esto, dos altas simultáneas a capacity=1 pueden leer "ocupadas=0"
        // ambas antes de que cualquiera haga commit, y las dos terminar ENROLLED.
        courseSubjectSectionRepository.findByIdForUpdate(section.getId())
                .orElseThrow(() -> new SectionNotFoundException(section.getId()));
        int ocupadas = courseSubjectSectionEnrollmentRepository
                .findByCourseSubjectSectionIdAndStatusIn(section.getId(),
                        List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR))
                .size();
        return section.getCapacity() == null || ocupadas < section.getCapacity()
                ? EnrollmentStatus.ENROLLED : EnrollmentStatus.WAITLISTED;
    }

    @Transactional
    public void darDeBaja(Long id) {
        CourseEnrollment enrollment = findOrThrow(id);
        if (enrollment.getStatus() != EnrollmentStatus.ENROLLED) {
            throw new CannotWithdrawException("La matriculación " + id + " no está ENROLLED, no se puede dar de baja");
        }
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        courseEnrollmentRepository.save(enrollment);

        // A diferencia de ProgramEnrollment (donde las SubjectSectionEnrollment las elige el
        // alumno una por una y no se tocan al dar de baja la carrera, para preservar su
        // historial de cursadas independiente), las CourseSubjectSectionEnrollment de un Course
        // se crean TODAS juntas en una única alta automática (decisión 15) — son un detalle de
        // esa alta, no elecciones independientes del alumno. Sin cascada acá, el cupo de cada
        // módulo queda ocupado para siempre por alguien que ya no cursa nada.
        LocalDateTime now = LocalDateTime.now();
        for (CourseSubjectSectionEnrollment sub : courseSubjectSectionEnrollmentRepository.findByCourseEnrollmentId(id)) {
            EnrollmentStatus estadoAnterior = sub.getStatus();
            if (estadoAnterior == EnrollmentStatus.ENROLLED || estadoAnterior == EnrollmentStatus.IN_PROGRESS
                    || estadoAnterior == EnrollmentStatus.WAITLISTED) {
                sub.setStatus(EnrollmentStatus.WITHDRAWN);
                sub.setDroppedAt(now);
                courseSubjectSectionEnrollmentRepository.save(sub);
                if (estadoAnterior != EnrollmentStatus.WAITLISTED) {
                    promoverSiguienteEnListaDeEspera(sub.getCourseSubjectSection().getId());
                }
            }
        }
    }

    private void promoverSiguienteEnListaDeEspera(Long courseSubjectSectionId) {
        courseSubjectSectionRepository.findByIdForUpdate(courseSubjectSectionId)
                .orElseThrow(() -> new SectionNotFoundException(courseSubjectSectionId));
        courseSubjectSectionEnrollmentRepository
                .findFirstByCourseSubjectSectionIdAndStatusOrderByEnrolledAtAsc(courseSubjectSectionId, EnrollmentStatus.WAITLISTED)
                .ifPresent(siguiente -> {
                    siguiente.setStatus(EnrollmentStatus.ENROLLED);
                    courseSubjectSectionEnrollmentRepository.save(siguiente);
                });
    }

    public CourseEnrollment verDetalle(Long id) {
        return findOrThrow(id);
    }

    public List<CourseEnrollment> listarPorAlumno(Long studentId) {
        return courseEnrollmentRepository.findByStudentId(studentId);
    }

    public CourseProgressResponse calcularProgreso(Long id) {
        CourseEnrollment enrollment = findOrThrow(id);
        List<CourseSubject> modulos = courseSubjectRepository
                .findByCourseIdAndStatusOrderBySortOrder(enrollment.getCourse().getId(), StatusType.ACTIVE);

        List<CourseSubjectSectionEnrollment> inscripciones = courseSubjectSectionEnrollmentRepository
                .findByCourseEnrollmentId(enrollment.getId());

        List<String> modulosPendientes = modulos.stream()
                .filter(modulo -> inscripciones.stream().noneMatch(inscripcion ->
                        inscripcion.getCourseSubjectSection().getCourseSubject().getId().equals(modulo.getId())
                                && inscripcion.getStatus() == EnrollmentStatus.APPROVED))
                .map(CourseSubject::getName)
                .toList();

        return new CourseProgressResponse(enrollment.getId(), modulosPendientes, modulosPendientes.isEmpty());
    }

    private void validarStudentHabilitado(Student student) {
        if (student.getOnboardingStatus() != StudentOnboardingStatus.CONFIRMADO) {
            throw new StudentNotConfirmedException(student.getId());
        }
        if (student.getPerson().getStatus() != StatusType.ACTIVE) {
            throw new StudentNotActiveException(student.getId());
        }
    }

    private CourseEnrollment findOrThrow(Long id) {
        return courseEnrollmentRepository.findById(id)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(id));
    }
}
