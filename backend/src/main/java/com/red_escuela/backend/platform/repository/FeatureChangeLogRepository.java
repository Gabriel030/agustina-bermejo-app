package com.red_escuela.backend.platform.repository;

import com.red_escuela.backend.platform.entity.FeatureChangeLog;
import com.red_escuela.backend.platform.model.FeatureCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface FeatureChangeLogRepository extends JpaRepository<FeatureChangeLog, Long>, JpaSpecificationExecutor<FeatureChangeLog> {

    List<FeatureChangeLog> findAllByOrderByChangedAtDescIdDesc();

    Page<FeatureChangeLog> findAllByOrderByChangedAtDescIdDesc(Pageable pageable);

    Page<FeatureChangeLog> findAllByCodeOrderByChangedAtDescIdDesc(FeatureCode code, Pageable pageable);

    Optional<FeatureChangeLog> findFirstByCodeOrderByChangedAtDescIdDesc(FeatureCode code);

    boolean existsByCode(FeatureCode code);
}
