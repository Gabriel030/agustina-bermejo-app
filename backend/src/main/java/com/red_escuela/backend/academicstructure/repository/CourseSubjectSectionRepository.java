package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseSubjectSectionRepository extends JpaRepository<CourseSubjectSection, Long> {

    @Query("select new com.red_escuela.backend.academicstructure.repository.EndedSection(s.id, s.term.endDate) from CourseSubjectSection s where s.status = :status and s.term.endDate >= :from and s.term.endDate < :until")
    List<EndedSection> findEndedByStatus(@Param("status") StatusType status, @Param("from") java.time.LocalDateTime from, @Param("until") java.time.LocalDateTime until);


    boolean existsByCode(String code);

    List<CourseSubjectSection> findByCourseSubjectId(Long courseSubjectId);

    List<CourseSubjectSection> findByCourseSubjectCourseId(Long courseId);

    List<CourseSubjectSection> findByAcademicOfferingCycleId(Long cycleId);

    @Query("select new com.red_escuela.backend.academicstructure.repository.CycleSectionCount(s.academicOfferingCycle.id, count(s)) from CourseSubjectSection s where s.academicOfferingCycle.id in :cycleIds group by s.academicOfferingCycle.id")
    List<CycleSectionCount> countByCycleIds(@Param("cycleIds") Collection<Long> cycleIds);

    List<CourseSubjectSection> findByCourseSubjectIdAndStatus(Long courseSubjectId, StatusType status);

    List<CourseSubjectSection> findByTermId(Long termId);

    boolean existsByEvaluationSchemeId(Long evaluationSchemeId);

    @Query("select s.evaluationScheme.id from CourseSubjectSection s where s.id = :id")
    Optional<Long> findEvaluationSchemeIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CourseSubjectSection s where s.id = :id")
    Optional<CourseSubjectSection> findByIdForUpdate(@Param("id") Long id);

    @Query("select s from CourseSubjectSection s left join fetch s.evaluationScheme left join fetch s.term "
            + "left join fetch s.academicOfferingCycle c left join fetch c.term where s.id = :id")
    Optional<CourseSubjectSection> findByIdWithSchemeAndTerms(@Param("id") Long id);
}
