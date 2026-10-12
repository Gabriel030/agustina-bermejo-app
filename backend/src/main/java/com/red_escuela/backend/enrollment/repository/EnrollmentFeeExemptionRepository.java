package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.EnrollmentFeeExemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentFeeExemptionRepository extends JpaRepository<EnrollmentFeeExemption, Long> {

    Optional<EnrollmentFeeExemption> findByProgramEnrollmentIdAndTemplateIdAndSlotAndRevokedAtIsNull(Long programEnrollmentId, Long templateId, Integer slot);

    List<EnrollmentFeeExemption> findByProgramEnrollmentIdOrderByGrantedAtDesc(Long programEnrollmentId);
}
