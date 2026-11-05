package com.red_escuela.backend.platform.repository;

import com.red_escuela.backend.platform.entity.PlatformFeature;
import com.red_escuela.backend.platform.model.FeatureCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlatformFeatureRepository extends JpaRepository<PlatformFeature, FeatureCode> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from PlatformFeature f where f.code = :code")
    Optional<PlatformFeature> findByCodeForUpdate(@Param("code") FeatureCode code);
}
