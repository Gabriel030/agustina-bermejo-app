package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommissionClosureRepository extends JpaRepository<CommissionClosure, Long> {

    Optional<CommissionClosure> findBySectionKindAndSectionId(SectionKind sectionKind, Long sectionId);

    @Query(value = "select nextval('closure_report_number_seq')", nativeQuery = true)
    long nextReportSequence();
}
