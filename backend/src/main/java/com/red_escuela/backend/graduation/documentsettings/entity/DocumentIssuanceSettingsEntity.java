package com.red_escuela.backend.graduation.documentsettings.entity;

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
@Table(name = "document_issuance_settings")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentIssuanceSettingsEntity {

    public static final short SINGLETON_ID = 1;

    @Id
    @Builder.Default
    private Short id = SINGLETON_ID;

    @Version
    private Long version;

    @Column(name = "exam_registration_advance_days", nullable = false)
    private int examRegistrationAdvanceDays;

    @Column(name = "exam_attendance_validity_days", nullable = false)
    private int examAttendanceValidityDays;

    @Column(name = "approved_subjects_validity_days", nullable = false)
    private int approvedSubjectsValidityDays;

    @Column(name = "daily_issuance_limit_per_type", nullable = false)
    private int dailyIssuanceLimitPerType;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by_id")
    private Long updatedById;

    public void apply(int examRegistrationAdvanceDays, int examAttendanceValidityDays, int approvedSubjectsValidityDays,
                      int dailyIssuanceLimitPerType, Long actorId, LocalDateTime now) {
        this.examRegistrationAdvanceDays = examRegistrationAdvanceDays;
        this.examAttendanceValidityDays = examAttendanceValidityDays;
        this.approvedSubjectsValidityDays = approvedSubjectsValidityDays;
        this.dailyIssuanceLimitPerType = dailyIssuanceLimitPerType;
        this.updatedById = actorId;
        this.updatedAt = now;
    }
}
