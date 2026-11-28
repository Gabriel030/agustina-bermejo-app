package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.api.SectionFinder;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SectionWithSchemeLoader {

    private static final String PROGRAM_QUERY = "select s, e from SubjectSection s "
            + "left join EvaluationScheme e on e.id = s.evaluationSchemeId "
            + "left join fetch s.term left join fetch s.academicOfferingCycle c left join fetch c.term where s.id = :id";
    private static final String COURSE_QUERY = "select s, e from CourseSubjectSection s "
            + "left join EvaluationScheme e on e.id = s.evaluationSchemeId "
            + "left join fetch s.term left join fetch s.academicOfferingCycle c left join fetch c.term where s.id = :id";

    private final EntityManager entityManager;
    private final SectionFinder sectionFinder;

    public SectionWithSchemeLoader(EntityManager entityManager, SectionFinder sectionFinder) {
        this.entityManager = entityManager;
        this.sectionFinder = sectionFinder;
    }

    public SubjectSection loadProgramSection(Long id) {
        return load(PROGRAM_QUERY, id, SubjectSection.class).orElseGet(() -> sectionFinder.findProgramSectionEntity(id));
    }

    public CourseSubjectSection loadCourseSection(Long id) {
        return load(COURSE_QUERY, id, CourseSubjectSection.class).orElseGet(() -> sectionFinder.findCourseSectionEntity(id));
    }

    private <T> Optional<T> load(String query, Long id, Class<T> type) {
        return entityManager.createQuery(query, Object[].class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst()
                .map(row -> type.cast(row[0]));
    }
}
