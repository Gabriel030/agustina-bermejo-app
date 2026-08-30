package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.InstructorRole;
import com.red_escuela.backend.entity.SubjectSectionInstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectSectionInstructorRepository extends JpaRepository<SubjectSectionInstructor, Long> {

    List<SubjectSectionInstructor> findBySectionId(Long sectionId);

    // Resolver las comisiones propias del Docente, para la autorización de "comisión propia".
    List<SubjectSectionInstructor> findByInstructorId(Long instructorId);

    boolean existsBySectionIdAndInstructorId(Long sectionId, Long instructorId);

    boolean existsBySectionIdAndInstructorIdAndRole(Long sectionId, Long instructorId, InstructorRole role);
}
