package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.CollectionsPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface CollectionsPolicyRepository extends JpaRepository<CollectionsPolicy, Long> {

    Optional<CollectionsPolicy> findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate effectiveFrom);
}
