package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectSectionRepository extends JpaRepository<SubjectSection, Long> {
    List<SubjectSection> findBySubjectId(Long subjectId);

    List<SubjectSection> findBySubjectIdAndStatus(Long subjectId, StatusType status);

    List<SubjectSection> findByTermId(Long termId);

    boolean existsByEvaluationSchemeId(Long evaluationSchemeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SubjectSection s where s.id = :id")
    Optional<SubjectSection> findByIdForUpdate(@Param("id") Long id);
}
