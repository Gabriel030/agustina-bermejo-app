package com.red_escuela.backend.billing.payment.repository;

import com.red_escuela.backend.billing.payment.domain.MpWebhookReceivedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MpWebhookReceivedEventRepository extends JpaRepository<MpWebhookReceivedEvent, Long> {
}
