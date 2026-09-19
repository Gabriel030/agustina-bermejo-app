package com.red_escuela.backend.repository;

import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationSchemeTestRepository extends JpaRepository<EvaluationScheme, Long> {
}
