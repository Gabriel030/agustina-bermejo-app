package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSectionInstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseSubjectSectionInstructorRepository extends JpaRepository<CourseSubjectSectionInstructor, Long> {

    List<CourseSubjectSectionInstructor> findBySectionId(Long sectionId);

    // Resolver las comisiones propias del Docente, para la autorización de "comisión propia".
    List<CourseSubjectSectionInstructor> findByInstructorId(Long instructorId);

    boolean existsBySectionIdAndInstructorId(Long sectionId, Long instructorId);
}
