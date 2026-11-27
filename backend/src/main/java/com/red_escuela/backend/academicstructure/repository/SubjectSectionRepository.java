package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
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
public interface SubjectSectionRepository extends JpaRepository<SubjectSection, Long> {

    boolean existsByCode(String code);

    List<SubjectSection> findBySubjectId(Long subjectId);

    boolean existsBySubjectId(Long subjectId);

    List<SubjectSection> findByAcademicOfferingCycleId(Long cycleId);

    @Query("select new com.red_escuela.backend.academicstructure.repository.CycleSectionCount(s.academicOfferingCycle.id, count(s)) from SubjectSection s where s.academicOfferingCycle.id in :cycleIds group by s.academicOfferingCycle.id")
    List<CycleSectionCount> countByCycleIds(@Param("cycleIds") Collection<Long> cycleIds);

    List<SubjectSection> findBySubjectIdAndStatus(Long subjectId, StatusType status);

    List<SubjectSection> findByTermId(Long termId);

    boolean existsByEvaluationSchemeId(Long evaluationSchemeId);

    @Query("select s.evaluationScheme.id from SubjectSection s where s.id = :id")
    Optional<Long> findEvaluationSchemeIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SubjectSection s where s.id = :id")
    Optional<SubjectSection> findByIdForUpdate(@Param("id") Long id);

    @Query("select s from SubjectSection s left join fetch s.evaluationScheme left join fetch s.term "
            + "left join fetch s.academicOfferingCycle c left join fetch c.term where s.id = :id")
    Optional<SubjectSection> findByIdWithSchemeAndTerms(@Param("id") Long id);

    java.util.Optional<SubjectSection> findByCode(String code);

    @Query("select new com.red_escuela.backend.academicstructure.repository.NamedId(s.code, s.id) from SubjectSection s where s.code in :codes")
    java.util.List<NamedId> findNamedIdsByCodeIn(@Param("codes") java.util.Collection<String> codes);

    @Query("select new com.red_escuela.backend.academicstructure.repository.SectionTermStart(s.id, s.term.startDate) from SubjectSection s where s.id in :ids")
    java.util.List<SectionTermStart> findTermStartsByIdIn(@Param("ids") java.util.Collection<Long> ids);
}
