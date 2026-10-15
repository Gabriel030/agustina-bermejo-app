package com.red_escuela.backend.billing.discount.repository;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DiscountDefinitionRepository extends JpaRepository<DiscountDefinition, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DiscountDefinition d where d.id = :id")
    Optional<DiscountDefinition> findByIdForUpdate(@Param("id") Long id);
}
