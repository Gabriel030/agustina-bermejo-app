package com.red_escuela.backend.attendance.repository;

import com.red_escuela.backend.attendance.entity.ClassSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    Optional<ClassSession> findBySubjectSectionIdAndClassDate(Long subjectSectionId, LocalDate classDate);

    Optional<ClassSession> findByCourseSubjectSectionIdAndClassDate(Long courseSubjectSectionId, LocalDate classDate);

    List<ClassSession> findBySubjectSectionId(Long subjectSectionId);

    List<ClassSession> findByCourseSubjectSectionId(Long courseSubjectSectionId);
}
