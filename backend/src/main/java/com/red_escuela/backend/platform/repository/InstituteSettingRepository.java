package com.red_escuela.backend.platform.repository;

import com.red_escuela.backend.platform.entity.InstituteSetting;
import com.red_escuela.backend.platform.model.InstituteSettingCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InstituteSettingRepository extends JpaRepository<InstituteSetting, InstituteSettingCode> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InstituteSetting s where s.code = :code")
    Optional<InstituteSetting> findByCodeForUpdate(@Param("code") InstituteSettingCode code);
}
