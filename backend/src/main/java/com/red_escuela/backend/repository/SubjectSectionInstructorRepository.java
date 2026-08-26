package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.SubjectSectionInstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectSectionInstructorRepository extends JpaRepository<SubjectSectionInstructor, Long> {

    List<SubjectSectionInstructor> findBySectionId(Long sectionId);
}
