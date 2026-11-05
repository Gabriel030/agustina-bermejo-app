package com.red_escuela.backend.billing.run.repository;

import com.red_escuela.backend.billing.run.domain.BillingRunItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillingRunItemRepository extends JpaRepository<BillingRunItem, Long> {

    List<BillingRunItem> findByBillingRunIdOrderByIdAsc(Long billingRunId);
}
