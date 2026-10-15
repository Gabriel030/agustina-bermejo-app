package com.red_escuela.backend.enrollment.service;
import java.time.Clock;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.exception.StudentNotActiveException;
import com.red_escuela.backend.identity.exception.StudentNotConfirmedException;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.service.PersonRoleGranter;

import com.red_escuela.backend.enrollment.dto.CourseProgressResponse;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.enrollment.exception.AdmissionWindowClosedException;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInCourseException;
import com.red_escuela.backend.enrollment.exception.AmbiguousSectionException;
import com.red_escuela.backend.enrollment.exception.CannotWithdrawException;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.enrollment.exception.IncompleteSectionSetupException;
import com.red_escuela.backend.enrollment.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectRepository;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.red_escuela.backend.enrollment.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.billing.service.ScholarshipService;
import com.red_escuela.backend.billing.service.StudentDebtResolutionService;

@Service
public class CourseEnrollmentService {
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final CourseSubjectRepository courseSubjectRepository;
    private final CourseSubjectSectionRepository courseSubjectSectionRepository;
    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;
    private final PersonRoleGranter personRoleGranter;
    private final StudentDebtResolutionService studentDebtResolutionService;
    private final ScholarshipService scholarshipService;
    private final EnrollmentWithdrawalDateResolver withdrawalDateResolver;
    private final EnrollmentWithdrawalBillingPort withdrawalBillingPort;
    private final Clock clock;

    public CourseEnrollmentService(CourseEnrollmentRepository courseEnrollmentRepository,
            StudentRepository studentRepository,
            CourseRepository courseRepository,
            CourseSubjectRepository courseSubjectRepository,
            CourseSubjectSectionRepository courseSubjectSectionRepository,
            CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository,
            PersonRoleGranter personRoleGranter, StudentDebtResolutionService studentDebtResolutionService,
            ScholarshipService scholarshipService,
            EnrollmentWithdrawalDateResolver withdrawalDateResolver,
            EnrollmentWithdrawalBillingPort withdrawalBillingPort,
            Clock clock) {
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.courseSubjectRepository = courseSubjectRepository;
        this.courseSubjectSectionRepository = courseSubjectSectionRepository;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
        this.personRoleGranter = personRoleGranter;
        this.studentDebtResolutionService = studentDebtResolutionService;
        this.scholarshipService = scholarshipService;
        this.withdrawalDateResolver = withdrawalDateResolver;
        this.withdrawalBillingPort = withdrawalBillingPort;
        this.clock = clock;
    }

    @Transactional
    public CourseEnrollment enrollSelfService(Long studentId, CreateCourseEnrollmentRequest request) {
        CourseEnrollment enrollment = enroll(studentId, request);
        personRoleGranter.grantStudentRoleIfMissing(enrollment.getStudent().getPerson());
        return enrollment;
    }

    @Transactional
    public CourseEnrollment enroll(Long studentId, CreateCourseEnrollmentRequest request) {
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        studentDebtResolutionService.verifyNoDebt(studentId);
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new CourseNotFoundException(request.courseId()));
        validateCanEnroll(student, course);
        LocalDateTime now = LocalDateTime.now(clock);

        Map<CourseSubject, CourseSubjectSection> resueltas = new LinkedHashMap<>();
        for (CourseSubject courseSubject : courseSubjectRepository.findByCourseIdAndStatusOrderBySortOrder(course.getId(), StatusType.ACTIVE)) {
            resueltas.put(courseSubject, resolveSection(courseSubject, course, request));
        }

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(calculateStatusByCourseCapacity(course));
        enrollment.setEnrollmentDate(now);
        CourseEnrollment savedEnrollment = courseEnrollmentRepository.save(enrollment);

        for (CourseSubjectSection elegida : resueltas.values()) {
            courseSubjectSectionEnrollmentRepository.save(
                    createOrReactivateSubEnrollment(student, savedEnrollment, elegida, now));
        }

        return savedEnrollment;
    }

    void validateCanEnroll(Student student, Course course) {
        validateStudentEligible(student);

        if (course.getRegistrationStart() != null && course.getRegistrationEnd() != null
                && course.getRegistrationStart().isAfter(course.getRegistrationEnd())) {
            throw new InvalidWindowConfigurationException(
                    "El curso " + course.getId() + " tiene la ventana de inscripción mal configurada "
                            + "(registrationStart posterior a registrationEnd)");
        }
        if (!course.isRegistrationOpenAt(LocalDateTime.now(clock))) {
            throw new AdmissionWindowClosedException(
                    "Fuera de la ventana de inscripción del curso " + course.getId());
        }

        List<CourseEnrollment> activas = courseEnrollmentRepository.findByStudentIdAndCourseIdAndStatusIn(
                student.getId(), course.getId(),
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.WAITLISTED));
        if (!activas.isEmpty()) {
            throw new AlreadyEnrolledInCourseException(student.getId(), course.getId());
        }
    }

    private CourseSubjectSection resolveSection(CourseSubject courseSubject, Course course, CreateCourseEnrollmentRequest request) {
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

    private CourseSubjectSectionEnrollment createOrReactivateSubEnrollment(
            Student student, CourseEnrollment enrollment, CourseSubjectSection elegida, LocalDateTime now) {
        Optional<CourseSubjectSectionEnrollment> existente = courseSubjectSectionEnrollmentRepository
                .findByStudentIdAndCourseSubjectSectionId(student.getId(), elegida.getId());
        if (existente.isPresent() && existente.get().getStatus() == EnrollmentStatus.WITHDRAWN) {
            CourseSubjectSectionEnrollment reactivada = existente.get();
            reactivada.setCourseEnrollment(enrollment);
            reactivada.setStatus(calculateStatusByCapacity(elegida));
            reactivada.setDroppedAt(null);
            return reactivada;
        }

        CourseSubjectSectionEnrollment nueva = new CourseSubjectSectionEnrollment();
        nueva.setStudent(student);
        nueva.setCourseEnrollment(enrollment);
        nueva.setCourseSubjectSection(elegida);
        nueva.setStatus(calculateStatusByCapacity(elegida));
        nueva.setEnrolledAt(now);
        nueva.setAttemptNumber(1);
        return nueva;
    }

    private EnrollmentStatus calculateStatusByCourseCapacity(Course course) {
        courseRepository.findByIdForUpdate(course.getId())
                .orElseThrow(() -> new CourseNotFoundException(course.getId()));
        return course.hasCapacityFor(countOccupiedSeats(course.getId())) ? EnrollmentStatus.ENROLLED : EnrollmentStatus.WAITLISTED;
    }

    public long countOccupiedSeats(Long courseId) {
        return courseEnrollmentRepository.countByCourseIdAndStatusIn(
                courseId, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS));
    }

    private EnrollmentStatus calculateStatusByCapacity(CourseSubjectSection section) {
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
    public void withdraw(Long id, EnrollmentWithdrawal withdrawal) {
        CourseEnrollment enrollment = findOrThrow(id);
        if (enrollment.getStatus() != EnrollmentStatus.ENROLLED && enrollment.getStatus() != EnrollmentStatus.WAITLISTED) {
            throw new CannotWithdrawException("La matriculación " + id + " no está ENROLLED/WAITLISTED, no se puede dar de baja");
        }
        boolean liberaCupoDelCurso = enrollment.getStatus() == EnrollmentStatus.ENROLLED;
        LocalDate effectiveDate = withdrawalDateResolver.resolveEffectiveDate(withdrawal, enrollment.getEnrollmentDate());
        LocalDate registeredOn = withdrawalDateResolver.registrationDate();
        enrollment.markWithdrawn(effectiveDate, withdrawalDateResolver.completionMomentOf(effectiveDate), registeredOn,
                withdrawal.registeredByPersonId());
        courseEnrollmentRepository.save(enrollment);
        scholarshipService.cancelActiveForCourse(id, "Enrollment dado de baja");
        withdrawalBillingPort.registerWithdrawalForBilling(WithdrawnEnrollmentFacts.ofCourseEnrollment(
                enrollment.getStudent().getId(), id, effectiveDate, registeredOn, withdrawal.cause()));
        cancelModuleEnrollments(enrollment);
        if (liberaCupoDelCurso) {
            promoteNextFromCourseWaitlist(enrollment.getCourse());
        }
    }

    void cancelModuleEnrollments(CourseEnrollment enrollment) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (CourseSubjectSectionEnrollment sub : courseSubjectSectionEnrollmentRepository.findByCourseEnrollmentId(enrollment.getId())) {
            EnrollmentStatus estadoAnterior = sub.getStatus();
            if (estadoAnterior == EnrollmentStatus.ENROLLED || estadoAnterior == EnrollmentStatus.IN_PROGRESS
                    || estadoAnterior == EnrollmentStatus.WAITLISTED) {
                sub.setStatus(EnrollmentStatus.WITHDRAWN);
                sub.setDroppedAt(now);
                courseSubjectSectionEnrollmentRepository.save(sub);
                if (estadoAnterior != EnrollmentStatus.WAITLISTED) {
                    promoteNextFromWaitlist(sub.getCourseSubjectSection().getId());
                }
            }
        }
    }

    private void promoteNextFromCourseWaitlist(Course course) {
        courseRepository.findByIdForUpdate(course.getId())
                .orElseThrow(() -> new CourseNotFoundException(course.getId()));
        courseEnrollmentRepository
                .findFirstByCourseIdAndStatusOrderByEnrollmentDateAsc(course.getId(), EnrollmentStatus.WAITLISTED)
                .ifPresent(siguiente -> {
                    siguiente.setStatus(EnrollmentStatus.ENROLLED);
                    courseEnrollmentRepository.save(siguiente);
                });
    }

    private void promoteNextFromWaitlist(Long courseSubjectSectionId) {
        courseSubjectSectionRepository.findByIdForUpdate(courseSubjectSectionId)
                .orElseThrow(() -> new SectionNotFoundException(courseSubjectSectionId));
        courseSubjectSectionEnrollmentRepository
                .findFirstByCourseSubjectSectionIdAndStatusOrderByEnrolledAtAsc(courseSubjectSectionId, EnrollmentStatus.WAITLISTED)
                .ifPresent(siguiente -> {
                    siguiente.setStatus(EnrollmentStatus.ENROLLED);
                    courseSubjectSectionEnrollmentRepository.save(siguiente);
                });
    }

    public CourseEnrollment get(Long id) {
        return findOrThrow(id);
    }

    public List<CourseEnrollment> listByStudent(Long studentId) {
        return courseEnrollmentRepository.findByStudentId(studentId);
    }

    public CourseProgressResponse calculateProgress(Long id) {
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

    private void validateStudentEligible(Student student) {
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
