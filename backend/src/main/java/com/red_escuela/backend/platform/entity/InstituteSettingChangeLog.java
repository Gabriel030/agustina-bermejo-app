package com.red_escuela.backend.platform.entity;

import com.red_escuela.backend.platform.model.InstituteSettingCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "institute_setting_change_log")
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@lombok.AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InstituteSettingChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 40)
    private InstituteSettingCode code;

    @Column(name = "before_enabled")
    private Boolean beforeEnabled;

    @Column(name = "after_enabled", nullable = false)
    private boolean afterEnabled;

    @Column(name = "actor_person_id")
    private Long actorPersonId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
