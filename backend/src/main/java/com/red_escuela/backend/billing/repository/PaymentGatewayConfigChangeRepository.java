package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.PaymentGatewayConfigChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentGatewayConfigChangeRepository extends JpaRepository<PaymentGatewayConfigChange, Long> {

    List<PaymentGatewayConfigChange> findAllByOrderByChangedAtDescIdDesc();
}
