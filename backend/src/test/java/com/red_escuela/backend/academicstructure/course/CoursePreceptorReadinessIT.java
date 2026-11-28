package com.red_escuela.backend.academicstructure.course;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.RequesterAuthentication;
import com.red_escuela.backend.academicstructure.domain.CourseReadinessItemCode;
import com.red_escuela.backend.academicstructure.dto.CourseReadinessItemResponse;
import com.red_escuela.backend.academicstructure.dto.CreateCourseRequest;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.CourseCycleNotReadyException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.service.CourseReadinessChecker;
import com.red_escuela.backend.academicstructure.service.CourseService;
import com.red_escuela.backend.academicstructure.service.CycleApproval;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import com.red_escuela.backend.identity.exception.CourseResponsibleRequiredException;
import com.red_escuela.backend.identity.exception.PreceptorRoleRequiredException;
import com.red_escuela.backend.identity.repository.PreceptorAssignmentRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.PreceptorTestFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoursePreceptorReadinessIT extends AbstractIntegrationTest {

    @Autowired private CourseService courseService;
    @Autowired private CourseReadinessChecker readinessChecker;
    @Autowired private CycleApproval cycleApproval;
    @Autowired private AcademicOfferingCycleRepository cycleRepository;
    @Autowired private PreceptorAssignmentRepository assignments;
    @Autowired private PreceptorTestFactory preceptors;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    private CycleTestFixtures fixtures;
    private Term term;
    private Person actor;

    @BeforeEach
    void seed() {
        fixtures = new CycleTestFixtures(entityManager);
        term = fixtures.term(2040 + (int) (System.nanoTime() % 50));
        actor = RequesterAuthentication.authenticateNewRequester(entityManager);
    }

    @AfterEach
    void clearAuthentication() {
        RequesterAuthentication.clear();
    }

    @Test
    void courseCreationRequiresAtLeastOneResponsiblePreceptor() {
        assertThatThrownBy(() -> courseService.create(request(List.of())))
                .isInstanceOf(CourseResponsibleRequiredException.class);
    }

    @Test
    void courseCreationRejectsAResponsibleWithoutThePreceptorRole() {
        Long teacher = preceptors.createPersonWithRole("DOCENTE");

        assertThatThrownBy(() -> courseService.create(request(List.of(teacher))))
                .isInstanceOf(PreceptorRoleRequiredException.class);
    }

    @Test
    void courseCreationStoresTheDistinctResponsiblePreceptors() {
        Long first = preceptors.createPreceptorId();
        Long second = preceptors.createPreceptorId();

        Course course = courseService.create(request(List.of(first, second, first)));

        assertThat(jdbc.queryForList("select person_id from preceptor_assignment where course_id = ?", Long.class, course.getId()))
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void aLegacyCourseWithoutResponsibleOrSectionsIsReportedIncompleteButNotBlocked() {
        Course course = persistCourse();

        var readiness = readinessChecker.check(course.getId());

        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.complete()).isFalse();
        assertThat(item(readiness.items(), CourseReadinessItemCode.COURSE_RESPONSIBLE).fulfilled()).isFalse();
        assertThat(item(readiness.items(), CourseReadinessItemCode.COURSE_SECTIONS).reasons()).containsExactly("El curso no tiene comisiones");
    }

    @Test
    void readinessBlocksWhileACommissionHasNoPreceptorAndListsIt() {
        Course course = persistCourse();
        CourseSubjectSection section = persistSection(course, null);

        var readiness = readinessChecker.check(course.getId());

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.missingBlockingCodes()).containsExactly("SECTION_PRECEPTORS");
        assertThat(item(readiness.items(), CourseReadinessItemCode.SECTION_PRECEPTORS).reasons())
                .containsExactly("Modulo (" + section.getName() + "): sin preceptor");
    }

    @Test
    void approvingACourseCycleWithACommissionWithoutPreceptorFailsListingTheMissingItems() {
        Course course = persistCourse();
        AcademicOfferingCycle cycle = pendingCycle(course);
        persistSection(course, cycle);
        entityManager.flush();

        assertThatThrownBy(() -> cycleApproval.approve(cycle.getId(), "course-approval-1"))
                .isInstanceOfSatisfying(CourseCycleNotReadyException.class, exception -> {
                    assertThat(exception.details().get("missingItems")).isEqualTo(List.of("SECTION_PRECEPTORS"));
                    assertThat((List<?>) exception.details().get("reasons")).hasSize(1);
                });
        assertThat(cycleRepository.findById(cycle.getId()).orElseThrow().getStatus()).isEqualTo(AcademicOfferingCycleStatus.PENDING_APPROVAL);
    }

    @Test
    void approvingACourseCycleSucceedsOnceEveryCommissionHasAPreceptorEvenWithoutCourseResponsible() {
        Course course = persistCourse();
        AcademicOfferingCycle cycle = pendingCycle(course);
        CourseSubjectSection section = persistSection(course, cycle);
        assignments.save(PreceptorAssignment.builder()
                .personId(preceptors.createPreceptorId()).courseSectionId(section.getId()).createdBy(actor.getId()).build());
        entityManager.flush();

        cycleApproval.approve(cycle.getId(), "course-approval-2");

        assertThat(cycleRepository.findById(cycle.getId()).orElseThrow().getStatus()).isEqualTo(AcademicOfferingCycleStatus.APPROVED);
        assertThat(readinessChecker.check(course.getId()).ready()).isTrue();
    }

    private CreateCourseRequest request(List<Long> responsibleIds) {
        return new CreateCourseRequest("Curso " + System.nanoTime(), term.getId(), null, 20,
                Money.of(new BigDecimal("1000"), Currency.ARS), 3, null, null, null, null, null, responsibleIds);
    }

    private Course persistCourse() {
        Course course = new Course();
        course.setName("Curso " + System.nanoTime());
        course.setTerm(term);
        entityManager.persist(course);
        return course;
    }

    private AcademicOfferingCycle pendingCycle(Course course) {
        AcademicOfferingCycle cycle = new AcademicOfferingCycle();
        cycle.setCourse(course);
        cycle.setTerm(term);
        cycle.setStatus(AcademicOfferingCycleStatus.PENDING_APPROVAL);
        cycle.setCreatedBy(actor);
        cycle.setCreatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        entityManager.persist(cycle);
        return cycle;
    }

    private CourseSubjectSection persistSection(Course course, AcademicOfferingCycle cycle) {
        CourseSubject module = new CourseSubject();
        module.setCourse(course);
        module.setName("Modulo");
        entityManager.persist(module);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comision " + System.nanoTime());
        section.setCourseSubject(module);
        section.setTerm(term);
        section.setCapacity(20);
        section.setStatus(StatusType.INACTIVE);
        section.setAcademicOfferingCycle(cycle);
        entityManager.persist(section);
        entityManager.flush();
        return section;
    }

    private static CourseReadinessItemResponse item(List<CourseReadinessItemResponse> items, CourseReadinessItemCode code) {
        return items.stream().filter(item -> item.code() == code).findFirst().orElseThrow();
    }
}
