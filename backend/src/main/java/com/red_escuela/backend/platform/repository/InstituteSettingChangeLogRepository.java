package com.red_escuela.backend.platform.repository;

import com.red_escuela.backend.platform.entity.InstituteSettingChangeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstituteSettingChangeLogRepository extends JpaRepository<InstituteSettingChangeLog, Long> {

    Page<InstituteSettingChangeLog> findAllByOrderByChangedAtDescIdDesc(Pageable pageable);
}
