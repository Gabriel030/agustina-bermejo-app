package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentPlanOptionRepository extends JpaRepository<PaymentPlanOption, Long> {

    List<PaymentPlanOption> findByCourseId(Long courseId);

    List<PaymentPlanOption> findByProgramId(Long programId);
}
