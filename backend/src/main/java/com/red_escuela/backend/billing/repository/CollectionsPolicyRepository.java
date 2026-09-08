package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface CollectionsPolicyRepository extends JpaRepository<CollectionsPolicy, Long> {

    // Desempate por createdAt (no por id) cuando dos políticas comparten el mismo effectiveFrom
    // (mismo día): gana la CARGADA más recientemente — createdAt es exactamente el campo que
    // CollectionsPolicy declara para esto ("cuándo se CARGÓ, distinto de effectiveFrom"), a
    // diferencia de id, que solo aproxima el orden de creación mientras la secuencia nunca se
    // reinicie ni haya datos migrados/restaurados (corrección 2026-09-04, auditoría). id
    // como segundo desempate final, por si dos filas llegaran a compartir el mismo createdAt.
    Optional<CollectionsPolicy> findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(LocalDate effectiveFrom);
}
