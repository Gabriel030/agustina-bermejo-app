package com.red_escuela.backend.billing.payment.repository;

import com.red_escuela.backend.billing.payment.domain.GatewayCheckout;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatewayCheckoutRepository extends Repository<GatewayCheckout, Long> {

    GatewayCheckout save(GatewayCheckout checkout);

    Optional<GatewayCheckout> findByPublicReference(UUID publicReference);

    List<GatewayCheckout> findByInvoiceIdOrderByIdAsc(Long invoiceId);
}
