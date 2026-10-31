package com.red_escuela.backend.platform.repository;

import com.red_escuela.backend.platform.entity.FeatureChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeatureChangeLogRepository extends JpaRepository<FeatureChangeLog, Long> {

    List<FeatureChangeLog> findAllByOrderByChangedAtDescIdDesc();
}
