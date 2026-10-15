package com.red_escuela.backend.billing.collections.repository;

import com.red_escuela.backend.billing.collections.domain.LateFeeChargeCorrection;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface LateFeeChargeCorrectionRepository extends Repository<LateFeeChargeCorrection, Long> {

    LateFeeChargeCorrection save(LateFeeChargeCorrection correction);

    List<LateFeeChargeCorrection> findByLateFeeChargeIdOrderByFirstCorrectedDay(Long lateFeeChargeId);
}
