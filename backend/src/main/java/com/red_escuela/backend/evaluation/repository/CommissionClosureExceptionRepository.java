package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.CommissionClosureException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionClosureExceptionRepository extends JpaRepository<CommissionClosureException, Long> {

    List<CommissionClosureException> findByClosureIdAndRevokedAtIsNull(Long closureId);

    Optional<CommissionClosureException> findByClosureIdAndEnrollmentIdAndRevokedAtIsNull(Long closureId, Long enrollmentId);
}
