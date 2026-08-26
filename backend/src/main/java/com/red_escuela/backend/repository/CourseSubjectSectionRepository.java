package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.CourseSubjectSection;
import com.red_escuela.backend.entity.StatusType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseSubjectSectionRepository extends JpaRepository<CourseSubjectSection, Long> {

    List<CourseSubjectSection> findByCourseSubjectId(Long courseSubjectId);

    List<CourseSubjectSection> findByCourseSubjectIdAndStatus(Long courseSubjectId, StatusType status);

    List<CourseSubjectSection> findByTermId(Long termId);

    // Ver SubjectSectionRepository.findByIdForUpdate: mismo propósito de serialización de cupo.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CourseSubjectSection s where s.id = :id")
    Optional<CourseSubjectSection> findByIdForUpdate(@Param("id") Long id);
}
