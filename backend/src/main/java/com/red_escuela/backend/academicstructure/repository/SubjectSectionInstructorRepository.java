package com.red_escuela.backend.academicstructure.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectSectionInstructorRepository extends JpaRepository<SubjectSectionInstructor, Long> {
    List<SubjectSectionInstructor> findBySectionId(Long sectionId);

    List<SubjectSectionInstructor> findBySectionIdIn(java.util.Collection<Long> sectionIds);

    List<SubjectSectionInstructor> findByInstructorId(Long instructorId);

    boolean existsBySectionIdAndInstructorId(Long sectionId, Long instructorId);

    boolean existsBySectionIdAndInstructorIdAndRole(Long sectionId, Long instructorId, InstructorRole role);

    @Query("""
            select l from SubjectSectionInstructor l
            join fetch l.section s
            join fetch s.subject
            join fetch s.term
            left join fetch s.academicOfferingCycle
            where l.instructor.id = :instructorId
            """)
    List<SubjectSectionInstructor> findDetailedByInstructorId(@Param("instructorId") Long instructorId);
}
