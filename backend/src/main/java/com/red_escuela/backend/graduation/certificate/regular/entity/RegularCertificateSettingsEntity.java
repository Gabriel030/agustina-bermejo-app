package com.red_escuela.backend.graduation.certificate.regular.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "regular_certificate_settings")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegularCertificateSettingsEntity {

    public static final short SINGLETON_ID = 1;

    @Id
    @Builder.Default
    private Short id = SINGLETON_ID;

    @Version
    private Long version;

    @Column(name = "validity_days", nullable = false)
    private int validityDays;

    @Column(name = "exception_validity_days", nullable = false)
    private int exceptionValidityDays;

    @Column(name = "evidence_required", nullable = false)
    private boolean evidenceRequired;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by_id")
    private Long updatedById;

    public static RegularCertificateSettingsEntity from(RegularCertificateSettings settings) {
        RegularCertificateSettingsEntity entity = new RegularCertificateSettingsEntity();
        entity.id = SINGLETON_ID;
        entity.apply(settings, null, null);
        return entity;
    }

    public void apply(RegularCertificateSettings settings, Long actorId, LocalDateTime now) {
        this.validityDays = settings.validityDays().value();
        this.exceptionValidityDays = settings.exceptionValidityDays().value();
        this.evidenceRequired = settings.evidenceRequired();
        this.updatedById = actorId;
        this.updatedAt = now;
    }

    public RegularCertificateSettings toSettings() {
        return RegularCertificateSettings.of(validityDays, exceptionValidityDays, evidenceRequired);
    }
}
