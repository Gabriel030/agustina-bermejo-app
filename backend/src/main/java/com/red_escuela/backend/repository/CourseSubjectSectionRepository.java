package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.CourseSubjectSection;
import com.red_escuela.backend.entity.StatusType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseSubjectSectionRepository extends JpaRepository<CourseSubjectSection, Long> {

    List<CourseSubjectSection> findByCourseSubjectId(Long courseSubjectId);

    List<CourseSubjectSection> findByCourseSubjectIdAndStatus(Long courseSubjectId, StatusType status);

    List<CourseSubjectSection> findByTermId(Long termId);
}
