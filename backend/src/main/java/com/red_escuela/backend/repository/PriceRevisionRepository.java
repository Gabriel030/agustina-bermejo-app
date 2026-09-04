package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.FeeType;
import com.red_escuela.backend.entity.PriceRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface PriceRevisionRepository extends JpaRepository<PriceRevision, Long> {

    Optional<PriceRevision> findTopByCourseIdAndFeeTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            Long courseId, FeeType feeType, LocalDate effectiveFrom);

    Optional<PriceRevision> findTopByProgramIdAndFeeTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            Long programId, FeeType feeType, LocalDate effectiveFrom);
}
