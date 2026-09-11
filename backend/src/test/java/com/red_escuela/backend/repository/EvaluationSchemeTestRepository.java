package com.red_escuela.backend.repository;

import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * No existe un EvaluationSchemeRepository en main porque no hay ningún endpoint de alta para
 * esta entidad (se referencia por id, opcional, desde CreateSectionRequest/UpdateSectionRequest
 * — ver "Deuda técnica y gaps de QA" en Obsidian). Repository mínimo, solo para que
 * KarateFixtures.crearEvaluationScheme() pueda sembrar uno vía HTTP-less setup, igual que el
 * resto de los fixtures de Karate.
 */
public interface EvaluationSchemeTestRepository extends JpaRepository<EvaluationScheme, Long> {
}
