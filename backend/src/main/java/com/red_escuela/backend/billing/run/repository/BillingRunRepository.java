package com.red_escuela.backend.billing.run.repository;

import com.red_escuela.backend.billing.run.domain.BillingRun;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BillingRunRepository extends JpaRepository<BillingRun, Long> {

    List<BillingRun> findByNightlyBillingRunIdOrderByIdAsc(Long nightlyBillingRunId);

    List<BillingRun> findByNightlyBillingRunIdInOrderByIdAsc(Collection<Long> nightlyBillingRunIds);

    Optional<BillingRun> findByNightlyBillingRunIdAndStep(Long nightlyBillingRunId, BillingRunStep step);
}
