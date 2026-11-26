package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.entity.CatalogArea;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
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
class SetCourseCatalogVisibilityServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SetCourseCatalogVisibilityUseCase setCatalogVisibility;

    @Autowired
    private EntityManager entityManager;

    @Test
    void nonExistentCourseThrowsCourseNotFoundException() {
        assertThatThrownBy(() -> setCatalogVisibility.execute(new SetCatalogVisibilityCommand(999999L, true, null)))
                .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void firstPublicationSetsCatalogPublishedAtAndRepublishingKeepsIt() {
        Course course = persistCourse("Curso Primera Publicacion");

        var hidden = setCatalogVisibility.execute(new SetCatalogVisibilityCommand(course.getId(), false, null));
        assertThat(hidden.getCatalogPublishedAt()).isNull();

        var firstPublication = setCatalogVisibility.execute(new SetCatalogVisibilityCommand(course.getId(), true, CatalogArea.HEALTH));
        assertThat(firstPublication.getCatalogPublishedAt()).isNotNull();
        assertThat(firstPublication.getArea()).isEqualTo("HEALTH");

        setCatalogVisibility.execute(new SetCatalogVisibilityCommand(course.getId(), false, null));
        var republication = setCatalogVisibility.execute(new SetCatalogVisibilityCommand(course.getId(), true, null));
        assertThat(republication.getCatalogPublishedAt()).isEqualTo(firstPublication.getCatalogPublishedAt());
        assertThat(republication.getArea()).isEqualTo("HEALTH");
    }

    private Course persistCourse(String name) {
        Course course = new Course();
        course.setName(name);
        course.setTerm(new CycleTestFixtures(entityManager).term(2040 + (int) (System.nanoTime() % 50)));
        entityManager.persist(course);
        entityManager.flush();
        return course;
    }
}
