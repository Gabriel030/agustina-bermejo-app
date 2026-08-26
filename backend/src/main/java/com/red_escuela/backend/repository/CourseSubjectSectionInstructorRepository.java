package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.CourseSubjectSectionInstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseSubjectSectionInstructorRepository extends JpaRepository<CourseSubjectSectionInstructor, Long> {

    List<CourseSubjectSectionInstructor> findBySectionId(Long sectionId);
}
