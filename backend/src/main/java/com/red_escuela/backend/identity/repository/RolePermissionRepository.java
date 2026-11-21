package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    Optional<RolePermission> findByRoleAndPermissionId(String role, Long permissionId);
}
