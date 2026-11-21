package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.PermissionModuleModeSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PermissionModuleModeSettingRepository extends JpaRepository<PermissionModuleModeSetting, Long> {

    Optional<PermissionModuleModeSetting> findByRoleAndModule(String role, String module);
}
