package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.LateFeePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LateFeePolicyRepository extends JpaRepository<LateFeePolicy, Long> {

    List<LateFeePolicy> findAllByOrderByVigenteDesdeDesc();

    Optional<LateFeePolicy> findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate fecha);
}
