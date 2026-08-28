package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.BillingPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillingPeriodRepository extends JpaRepository<BillingPeriod, Long> {

    List<BillingPeriod> findAllByOrderByVigenteDesdeDesc();
}
