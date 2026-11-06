package com.red_escuela.backend.platform.entity;

import com.red_escuela.backend.platform.domain.InstituteSettingState;
import com.red_escuela.backend.platform.model.InstituteSettingCode;
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
@Table(name = "institute_setting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstituteSetting {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 40)
    private InstituteSettingCode code;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by_id")
    private Long updatedById;

    public InstituteSettingState toState() {
        return new InstituteSettingState(enabled, version);
    }

    public void changeTo(boolean newEnabled, LocalDateTime changedAt, Long actorPersonId) {
        this.enabled = newEnabled;
        this.updatedAt = changedAt;
        this.updatedById = actorPersonId;
    }
}
