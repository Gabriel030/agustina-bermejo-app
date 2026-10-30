package com.red_escuela.backend.enrollment.service;
import java.time.Clock;
import com.red_escuela.backend.academicstructure.api.CourseCatalogFinder;
import com.red_escuela.backend.academicstructure.api.CourseModuleView;
import com.red_escuela.backend.academicstructure.api.CourseRegistrationView;
import com.red_escuela.backend.academicstructure.api.CourseSectionView;
import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.api.StudentEnrollmentEligibility;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.identity.api.StudentRoleGranter;

import com.red_escuela.backend.enrollment.dto.CourseProgressResponse;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.AdmissionWindowClosedException;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInCourseException;
import com.red_escuela.backend.enrollment.exception.AmbiguousSectionException;
import com.red_escuela.backend.enrollment.exception.CannotWithdrawException;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.IncompleteSectionSetupException;
import com.red_escuela.backend.enrollment.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.red_escuela.backend.enrollment.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.billing.api.EnrollmentDebtGuard;

@Service
public class CourseEnrollmentService {
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final StudentFinder studentFinder;
    private final StudentEnrollmentEligibility studentEnrollmentEligibility;
    private final CourseCatalogFinder courseCatalogFinder;
    private final SectionFinder sectionFinder;
    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;
    private final StudentRoleGranter studentRoleGranter;
    private final EnrollmentDebtGuard enrollmentDebtGuard;
    private final EnrollmentWithdrawalDateResolver withdrawalDateResolver;
    private final EnrollmentWithdrawalBillingPort withdrawalBillingPort;
    private final Clock clock;

    public CourseEnrollmentService(CourseEnrollmentRepository courseEnrollmentRepository,
            StudentFinder studentFinder,
            StudentEnrollmentEligibility studentEnrollmentEligibility,
            CourseCatalogFinder courseCatalogFinder,
            SectionFinder sectionFinder,
            CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository,
            StudentRoleGranter studentRoleGranter, EnrollmentDebtGuard enrollmentDebtGuard,
            EnrollmentWithdrawalDateResolver withdrawalDateResolver,
            EnrollmentWithdrawalBillingPort withdrawalBillingPort,
            Clock clock) {
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.studentFinder = studentFinder;
        this.studentEnrollmentEligibility = studentEnrollmentEligibility;
        this.courseCatalogFinder = courseCatalogFinder;
        this.sectionFinder = sectionFinder;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
        this.studentRoleGranter = studentRoleGranter;
        this.enrollmentDebtGuard = enrollmentDebtGuard;
        this.withdrawalDateResolver = withdrawalDateResolver;
        this.withdrawalBillingPort = withdrawalBillingPort;
        this.clock = clock;
    }

    @Transactional
    public CourseEnrollment enrollSelfService(Long studentId, CreateCourseEnrollmentRequest request) {
        CourseEnrollment enrollment = enroll(studentId, request);
        studentRoleGranter.grantStudentRoleIfMissing(enrollment.getStudent().getPerson());
        return enrollment;
    }

    @Transactional
    public CourseEnrollment enroll(Long studentId, CreateCourseEnrollmentRequest request) {
        Student student = studentFinder.requireByIdForUpdate(studentId);
        enrollmentDebtGuard.verifyNoOutstandingDebt(studentId);
        CourseRegistrationView registration = validateCanEnroll(student, request.courseId());
        LocalDateTime now = LocalDateTime.now(clock);

        List<CourseSectionView> resueltas = new ArrayList<>();
        for (CourseModuleView module : courseCatalogFinder.findActiveModules(registration.courseId())) {
            resueltas.add(resolveSection(module.id(), registration.termId(), request));
        }

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(courseCatalogFinder.findCourseEntity(registration.courseId()));
        enrollment.setStatus(calculateStatusByCourseCapacity(registration.courseId()));
        enrollment.setEnrollmentDate(now);
        CourseEnrollment savedEnrollment = courseEnrollmentRepository.save(enrollment);

        for (CourseSectionView elegida : resueltas) {
            courseSubjectSectionEnrollmentRepository.save(
                    createOrReactivateSubEnrollment(student, savedEnrollment, elegida, now));
        }

        return savedEnrollment;
    }

    CourseRegistrationView validateCanEnroll(Student student, Long courseId) {
        studentEnrollmentEligibility.requireConfirmedAndActive(student);

        CourseRegistrationView registration = courseCatalogFinder.findRegistration(courseId, LocalDateTime.now(clock));
        if (registration.windowInverted()) {
            throw new InvalidWindowConfigurationException(
                    "El curso " + courseId + " tiene la ventana de inscripción mal configurada "
                            + "(registrationStart posterior a registrationEnd)");
        }
        if (!registration.open()) {
            throw new AdmissionWindowClosedException(
                    "Fuera de la ventana de inscripción del curso " + courseId);
        }

        List<CourseEnrollment> activas = courseEnrollmentRepository.findByStudentIdAndCourseIdAndStatusIn(
                student.getId(), courseId,
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.WAITLISTED));
        if (!activas.isEmpty()) {
            throw new AlreadyEnrolledInCourseException(student.getId(), courseId);
        }
        return registration;
    }

    private CourseSectionView resolveSection(Long courseSubjectId, Long termId, CreateCourseEnrollmentRequest request) {
        List<CourseSectionView> secciones = courseCatalogFinder.findActiveSectionsOfModule(courseSubjectId)
                .stream()
                .filter(section -> section.termId().equals(termId))
                .toList();

        if (secciones.isEmpty()) {
            throw new IncompleteSectionSetupException(courseSubjectId);
        }
        if (secciones.size() == 1) {
            return secciones.get(0);
        }

        Long elegidaId = request.sectionOverrides() == null ? null : request.sectionOverrides().get(courseSubjectId);
        if (elegidaId == null) {
            throw new AmbiguousSectionException(courseSubjectId);
        }
        return secciones.stream()
                .filter(section -> section.id().equals(elegidaId))
                .findFirst()
                .orElseThrow(() -> new AmbiguousSectionException(courseSubjectId));
    }

    private CourseSubjectSectionEnrollment createOrReactivateSubEnrollment(
            Student student, CourseEnrollment enrollment, CourseSectionView elegida, LocalDateTime now) {
        Optional<CourseSubjectSectionEnrollment> existente = courseSubjectSectionEnrollmentRepository
                .findByStudentIdAndCourseSubjectSectionId(student.getId(), elegida.id());
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
        nueva.setCourseSubjectSection(sectionFinder.findCourseSectionEntity(elegida.id()));
        nueva.setStatus(calculateStatusByCapacity(elegida));
        nueva.setEnrolledAt(now);
        nueva.setAttemptNumber(1);
        return nueva;
    }

    private EnrollmentStatus calculateStatusByCourseCapacity(Long courseId) {
        courseCatalogFinder.lockCourse(courseId);
        return courseCatalogFinder.hasCapacityFor(courseId, countOccupiedSeats(courseId))
                ? EnrollmentStatus.ENROLLED : EnrollmentStatus.WAITLISTED;
    }

    public long countOccupiedSeats(Long courseId) {
        return courseEnrollmentRepository.countByCourseIdAndStatusIn(
                courseId, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS));
    }

    private EnrollmentStatus calculateStatusByCapacity(CourseSectionView section) {
        sectionFinder.lockCourseSection(section.id());
        int ocupadas = courseSubjectSectionEnrollmentRepository
                .findByCourseSubjectSectionIdAndStatusIn(section.id(),
                        List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR))
                .size();
        return section.capacity() == null || ocupadas < section.capacity()
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
        withdrawalBillingPort.registerWithdrawalForBilling(WithdrawnEnrollmentFacts.ofCourseEnrollment(
                enrollment.getStudent().getId(), id, effectiveDate, registeredOn, withdrawal.cause()));
        cancelModuleEnrollments(enrollment);
        if (liberaCupoDelCurso) {
            promoteNextFromCourseWaitlist(enrollment.courseId());
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
                    promoteNextFromWaitlist(sub.courseSubjectSectionId());
                }
            }
        }
    }

    private void promoteNextFromCourseWaitlist(Long courseId) {
        courseCatalogFinder.lockCourse(courseId);
        courseEnrollmentRepository
                .findFirstByCourseIdAndStatusOrderByEnrollmentDateAsc(courseId, EnrollmentStatus.WAITLISTED)
                .ifPresent(siguiente -> {
                    siguiente.setStatus(EnrollmentStatus.ENROLLED);
                    courseEnrollmentRepository.save(siguiente);
                });
    }

    private void promoteNextFromWaitlist(Long courseSubjectSectionId) {
        sectionFinder.lockCourseSection(courseSubjectSectionId);
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
        List<CourseModuleView> modulos = courseCatalogFinder.findActiveModules(enrollment.courseId());

        List<CourseSubjectSectionEnrollment> inscripciones = courseSubjectSectionEnrollmentRepository
                .findByCourseEnrollmentId(enrollment.getId());

        List<String> modulosPendientes = modulos.stream()
                .filter(modulo -> inscripciones.stream().noneMatch(inscripcion ->
                        inscripcion.courseSubjectId().equals(modulo.id())
                                && inscripcion.getStatus() == EnrollmentStatus.APPROVED))
                .map(CourseModuleView::name)
                .toList();

        return new CourseProgressResponse(enrollment.getId(), modulosPendientes, modulosPendientes.isEmpty());
    }

    private CourseEnrollment findOrThrow(Long id) {
        return courseEnrollmentRepository.findById(id)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(id));
    }
}
