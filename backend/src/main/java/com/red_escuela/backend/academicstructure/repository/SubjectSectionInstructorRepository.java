package com.red_escuela.backend.academicstructure.repository;

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
}
