package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
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
}
