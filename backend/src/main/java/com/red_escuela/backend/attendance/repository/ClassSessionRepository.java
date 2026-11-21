package com.red_escuela.backend.attendance.repository;

import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    Optional<ClassSession> findBySubjectSectionIdAndClassDate(Long subjectSectionId, LocalDate classDate);

    Optional<ClassSession> findByCourseSubjectSectionIdAndClassDate(Long courseSubjectSectionId, LocalDate classDate);

    List<ClassSession> findBySubjectSectionIdInAndStatusAndClassDateBetween(
            Collection<Long> sectionIds, ClassSessionStatus status,
            LocalDate from, LocalDate to);

    List<ClassSession> findByCourseSubjectSectionIdInAndStatusAndClassDateBetween(
            Collection<Long> sectionIds, ClassSessionStatus status,
            LocalDate from, LocalDate to);

    List<ClassSession> findBySubjectSectionId(Long subjectSectionId);

    List<ClassSession> findByCourseSubjectSectionId(Long courseSubjectSectionId);
}
