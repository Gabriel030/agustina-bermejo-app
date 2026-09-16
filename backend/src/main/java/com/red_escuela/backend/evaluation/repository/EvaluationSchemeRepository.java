package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluationSchemeRepository extends JpaRepository<EvaluationScheme, Long> {
}
