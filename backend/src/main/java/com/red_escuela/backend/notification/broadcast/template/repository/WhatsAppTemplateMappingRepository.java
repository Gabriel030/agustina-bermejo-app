package com.red_escuela.backend.notification.broadcast.template.repository;

import com.red_escuela.backend.notification.broadcast.template.entity.WhatsAppTemplateMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhatsAppTemplateMappingRepository extends JpaRepository<WhatsAppTemplateMapping, Long> {

    Optional<WhatsAppTemplateMapping> findByMessageTemplateId(Long messageTemplateId);

    boolean existsByMessageTemplateId(Long messageTemplateId);
}
