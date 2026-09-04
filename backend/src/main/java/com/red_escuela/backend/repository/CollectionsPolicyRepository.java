package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.CollectionsPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface CollectionsPolicyRepository extends JpaRepository<CollectionsPolicy, Long> {

    // Desempate por id cuando dos políticas comparten el mismo effectiveFrom (mismo día): gana la
    // creada más recientemente, no una fila arbitraria — sin esto, el orden de empate queda
    // indefinido a criterio del motor de base de datos.
    Optional<CollectionsPolicy> findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(LocalDate effectiveFrom);
}
