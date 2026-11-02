package com.red_escuela.backend.platform.repository;

import com.red_escuela.backend.platform.entity.PlatformProvisioningLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformProvisioningLogRepository extends JpaRepository<PlatformProvisioningLog, Long> {

    Optional<PlatformProvisioningLog> findByIdempotencyKey(String idempotencyKey);
}
