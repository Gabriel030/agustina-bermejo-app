package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.PaymentPlanOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentPlanOptionRepository extends JpaRepository<PaymentPlanOption, Long> {

    List<PaymentPlanOption> findByCourseId(Long courseId);

    List<PaymentPlanOption> findByProgramId(Long programId);
}
