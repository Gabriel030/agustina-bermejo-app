package com.red_escuela.backend.platform.entity;

import com.red_escuela.backend.platform.domain.FeatureState;
import com.red_escuela.backend.platform.model.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "platform_feature")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformFeature {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 40)
    private FeatureCode code;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 10)
    private BillingMode mode;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by_id")
    private Long updatedById;

    public FeatureState toState() {
        return new FeatureState(enabled, mode, version);
    }

    public void changeTo(boolean newEnabled, BillingMode newMode, LocalDateTime changedAt, Long actorPersonId) {
        this.enabled = newEnabled;
        this.mode = newMode;
        this.updatedAt = changedAt;
        this.updatedById = actorPersonId;
    }
}
