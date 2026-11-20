package com.red_escuela.backend.notification.broadcast.template.entity;

import com.red_escuela.backend.notification.broadcast.template.domain.WhatsAppTemplateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "whatsapp_template_mapping")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class WhatsAppTemplateMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_template_id", nullable = false, updatable = false)
    private Long messageTemplateId;

    @Column(name = "provider_template_name", nullable = false, length = 120)
    private String providerTemplateName;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WhatsAppTemplateStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variable_order", nullable = false, columnDefinition = "jsonb")
    private List<String> variableOrder;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
