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
import java.util.ArrayList;
import java.util.List;

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

    public CourseEnrollmentService(
            CourseEnrollmentRepository courseEnrollmentRepository,
            StudentRepository studentRepository,
            CourseRepository courseRepository,
            CourseSubjectRepository courseSubjectRepository,
            CourseSubjectSectionRepository courseSubjectSectionRepository,
            CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository) {
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.courseSubjectRepository = courseSubjectRepository;
        this.courseSubjectSectionRepository = courseSubjectSectionRepository;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
    }

    @Transactional
    public CourseEnrollment matricular(Long studentId, CreateCourseEnrollmentRequest request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        validarStudentHabilitado(student);

        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new CourseNotFoundException(request.courseId()));

        LocalDateTime now = LocalDateTime.now();
        if (course.getRegistrationStart() != null && now.isBefore(course.getRegistrationStart())
                || course.getRegistrationEnd() != null && now.isAfter(course.getRegistrationEnd())) {
            throw new AdmissionWindowClosedException(
                    "Fuera de la ventana de inscripción del curso " + course.getId());
        }

        // CourseEnrollment.status usa EnrollmentStatus (sin PAUSED): la única activa posible es ENROLLED.
        List<CourseEnrollment> activas = courseEnrollmentRepository.findByStudentIdAndCourseIdAndStatusIn(
                studentId, course.getId(), List.of(EnrollmentStatus.ENROLLED));
        if (!activas.isEmpty()) {
            throw new AlreadyEnrolledInCourseException(studentId, course.getId());
        }

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(now);

        List<CourseSubjectSectionEnrollment> subEnrollments = new ArrayList<>();
        for (CourseSubject courseSubject : courseSubjectRepository.findByCourseIdAndStatusOrderBySortOrder(course.getId(), StatusType.ACTIVE)) {
            CourseSubjectSection elegida = resolverSeccion(courseSubject, course, request);
            subEnrollments.add(crearSubInscripcion(student, enrollment, elegida, now));
        }
        enrollment.setSectionEnrollments(subEnrollments);

        return courseEnrollmentRepository.save(enrollment);
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

    private CourseSubjectSectionEnrollment crearSubInscripcion(
            Student student, CourseEnrollment enrollment, CourseSubjectSection elegida, LocalDateTime now) {
        int ocupadas = courseSubjectSectionEnrollmentRepository
                .findByCourseSubjectSectionIdAndStatusIn(elegida.getId(),
                        List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR))
                .size();
        EnrollmentStatus status = elegida.getCapacity() == null || ocupadas < elegida.getCapacity()
                ? EnrollmentStatus.ENROLLED : EnrollmentStatus.WAITLISTED;

        CourseSubjectSectionEnrollment subEnrollment = new CourseSubjectSectionEnrollment();
        subEnrollment.setStudent(student);
        subEnrollment.setCourseEnrollment(enrollment);
        subEnrollment.setCourseSubjectSection(elegida);
        subEnrollment.setStatus(status);
        subEnrollment.setEnrolledAt(now);
        subEnrollment.setAttemptNumber(1);
        return subEnrollment;
    }

    @Transactional
    public void darDeBaja(Long id) {
        CourseEnrollment enrollment = findOrThrow(id);
        if (enrollment.getStatus() != EnrollmentStatus.ENROLLED) {
            throw new CannotWithdrawException("La matriculación " + id + " no está ENROLLED, no se puede dar de baja");
        }
        // Sin cascada a las CourseSubjectSectionEnrollment (mismo criterio que ProgramEnrollmentService).
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        courseEnrollmentRepository.save(enrollment);
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
