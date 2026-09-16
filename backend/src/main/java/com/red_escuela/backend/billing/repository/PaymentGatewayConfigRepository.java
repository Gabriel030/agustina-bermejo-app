package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.PaymentGatewayConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentGatewayConfigRepository extends JpaRepository<PaymentGatewayConfig, Long> {

    Optional<PaymentGatewayConfig> findFirstByOrderByIdAsc();
}
