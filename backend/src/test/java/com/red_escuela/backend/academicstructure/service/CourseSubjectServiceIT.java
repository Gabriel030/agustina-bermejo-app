package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.dto.CreateCourseSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateCourseSubjectRequest;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.exception.CourseSubjectNotFoundException;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseSubjectServiceIT extends AbstractIntegrationTest {

    private static final long MISSING_ID = 987_654_321L;

    @Autowired
    private CourseSubjectService courseSubjectService;
    @Autowired
    private EntityManager entityManager;

    private Course course;

    @BeforeEach
    void seedACourse() {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        course = Course.builder().name("Curso " + System.nanoTime()).term(fixtures.term(2040 + (int) (System.nanoTime() % 50)))
                .capacity(10).durationMonths(3).isActive(StatusType.ACTIVE).currency(com.red_escuela.backend.shared.domain.Currency.ARS).build();
        entityManager.persist(course);
        entityManager.flush();
    }

    private CourseSubject addModule(String name, Integer sortOrder) {
        return courseSubjectService.addModule(course.getId(),
                new CreateCourseSubjectRequest(name, sortOrder, 4, SubjectRecurrence.ANNUAL, "NUMERIC"));
    }

    @Test
    void addingAModuleStoresItsFieldsAndStartsActive() {
        CourseSubject module = addModule("Modulo 1", 1);

        assertThat(module.getId()).isNotNull();
        assertThat(module.getCourse().getId()).isEqualTo(course.getId());
        assertThat(module.getName()).isEqualTo("Modulo 1");
        assertThat(module.getSortOrder()).isEqualTo(1);
        assertThat(module.getWeeklyWorkload()).isEqualTo(4);
        assertThat(module.getSubjectRecurrence()).isEqualTo(SubjectRecurrence.ANNUAL);
        assertThat(module.getQualificationType()).isEqualTo("NUMERIC");
        assertThat(module.getStatus()).isEqualTo(StatusType.ACTIVE);
    }

    @Test
    void addingAModuleToAMissingCourseFailsWithNotFound() {
        assertThatThrownBy(() -> courseSubjectService.addModule(MISSING_ID,
                new CreateCourseSubjectRequest("X", 1, null, null, null))).isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void updatingOnlyTheNameKeepsEveryOtherField() {
        CourseSubject module = addModule("Viejo", 1);

        CourseSubject updated = courseSubjectService.update(module.getId(),
                new UpdateCourseSubjectRequest("Nuevo", null, null, null, null));

        assertThat(updated.getName()).isEqualTo("Nuevo");
        assertThat(updated.getSortOrder()).isEqualTo(1);
        assertThat(updated.getWeeklyWorkload()).isEqualTo(4);
        assertThat(updated.getSubjectRecurrence()).isEqualTo(SubjectRecurrence.ANNUAL);
        assertThat(updated.getQualificationType()).isEqualTo("NUMERIC");
    }

    @Test
    void updatingEveryFieldStoresTheNewValues() {
        CourseSubject module = addModule("Viejo", 1);

        CourseSubject updated = courseSubjectService.update(module.getId(),
                new UpdateCourseSubjectRequest(null, 7, 9, SubjectRecurrence.PROMOTIONAL, "CONCEPT"));

        assertThat(updated.getName()).isEqualTo("Viejo");
        assertThat(updated.getSortOrder()).isEqualTo(7);
        assertThat(updated.getWeeklyWorkload()).isEqualTo(9);
        assertThat(updated.getSubjectRecurrence()).isEqualTo(SubjectRecurrence.PROMOTIONAL);
        assertThat(updated.getQualificationType()).isEqualTo("CONCEPT");
    }

    @Test
    void updatingOrGettingAMissingModuleFailsWithNotFound() {
        assertThatThrownBy(() -> courseSubjectService.update(MISSING_ID, new UpdateCourseSubjectRequest("X", null, null, null, null)))
                .isInstanceOf(CourseSubjectNotFoundException.class);
        assertThatThrownBy(() -> courseSubjectService.get(MISSING_ID)).isInstanceOf(CourseSubjectNotFoundException.class);
    }

    @Test
    void listIsSortedBySortOrderAndFilteredByStatus() {
        CourseSubject third = addModule("Tercero", 3);
        CourseSubject first = addModule("Primero", 1);
        CourseSubject second = addModule("Segundo", 2);
        courseSubjectService.deactivate(second.getId());
        entityManager.flush();

        assertThat(courseSubjectService.listByCourse(course.getId(), null))
                .extracting(CourseSubject::getId).containsExactly(first.getId(), second.getId(), third.getId());
        assertThat(courseSubjectService.listByCourse(course.getId(), StatusType.ACTIVE))
                .extracting(CourseSubject::getId).containsExactly(first.getId(), third.getId());
        assertThat(courseSubjectService.listByCourse(course.getId(), StatusType.INACTIVE))
                .extracting(CourseSubject::getId).containsExactly(second.getId());
    }

    @Test
    void deactivatingAndReactivatingTogglesTheStatus() {
        CourseSubject module = addModule("Estado", 1);

        courseSubjectService.deactivate(module.getId());
        assertThat(courseSubjectService.get(module.getId()).getStatus()).isEqualTo(StatusType.INACTIVE);

        courseSubjectService.reactivate(module.getId());
        assertThat(courseSubjectService.get(module.getId()).getStatus()).isEqualTo(StatusType.ACTIVE);
    }

    @Test
    void deactivatingOrReactivatingAMissingModuleFailsWithNotFound() {
        assertThatThrownBy(() -> courseSubjectService.deactivate(MISSING_ID)).isInstanceOf(CourseSubjectNotFoundException.class);
        assertThatThrownBy(() -> courseSubjectService.reactivate(MISSING_ID)).isInstanceOf(CourseSubjectNotFoundException.class);
    }
}
