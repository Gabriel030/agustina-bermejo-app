package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface CollectionsPolicyRepository extends JpaRepository<CollectionsPolicy, Long> {
    Optional<CollectionsPolicy> findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(LocalDate effectiveFrom);
}
