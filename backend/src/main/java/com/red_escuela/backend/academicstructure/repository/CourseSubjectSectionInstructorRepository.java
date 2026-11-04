package com.red_escuela.backend.academicstructure.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSectionInstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseSubjectSectionInstructorRepository extends JpaRepository<CourseSubjectSectionInstructor, Long> {
    List<CourseSubjectSectionInstructor> findBySectionId(Long sectionId);

    List<CourseSubjectSectionInstructor> findByInstructorId(Long instructorId);

    boolean existsBySectionIdAndInstructorId(Long sectionId, Long instructorId);

    @Query("""
            select l from CourseSubjectSectionInstructor l
            join fetch l.section s
            join fetch s.courseSubject
            join fetch s.term
            left join fetch s.academicOfferingCycle
            where l.instructor.id = :instructorId
            """)
    List<CourseSubjectSectionInstructor> findDetailedByInstructorId(@Param("instructorId") Long instructorId);
}
