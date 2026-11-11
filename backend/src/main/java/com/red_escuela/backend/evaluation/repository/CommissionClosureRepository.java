package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommissionClosureRepository extends JpaRepository<CommissionClosure, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CommissionClosure c where c.id = :id")
    Optional<CommissionClosure> findByIdForUpdate(@Param("id") Long id);

    Optional<CommissionClosure> findBySectionKindAndSectionId(SectionKind sectionKind, Long sectionId);

    @Query(value = "select nextval('closure_report_number_seq')", nativeQuery = true)
    long nextReportSequence();

    @Modifying
    @Query(value = """
            insert into commission_closure (section_kind, section_id, status, report_version,
                closed_without_teacher_confirmation, lock_version)
            values (:#{#kind.name()}, :sectionId, 'DRAFT_AVAILABLE', 1, false, 0)
            on conflict (section_kind, section_id) do nothing
            """, nativeQuery = true)
    int insertDraftIfAbsent(@Param("kind") SectionKind kind, @Param("sectionId") Long sectionId);
}
