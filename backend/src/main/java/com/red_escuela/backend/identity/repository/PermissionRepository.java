package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByModuleAscCodeAsc();

    @Query("select distinct p.module from Permission p order by p.module")
    List<String> findDistinctModules();

    List<Permission> findAllByCodeIn(Collection<String> codes);
}
