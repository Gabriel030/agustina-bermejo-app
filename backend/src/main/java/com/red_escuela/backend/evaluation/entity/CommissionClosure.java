package com.red_escuela.backend.evaluation.entity;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "commission_closure")
public class CommissionClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_kind", nullable = false)
    private SectionKind sectionKind;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClosureStatus status;

    @Builder.Default
    @Column(name = "report_version", nullable = false)
    private int reportVersion = 1;

    @Column(name = "report_number", length = 20)
    private String reportNumber;

    @Column(name = "book", length = 50)
    private String book;

    @Column(name = "folio", length = 50)
    private String folio;

    @Column(name = "confirmed_by_id")
    private Long confirmedById;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "closed_by_id")
    private Long closedById;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_without_teacher_confirmation", nullable = false)
    private boolean closedWithoutTeacherConfirmation;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    public boolean isOpenForTeacher() {
        return status == ClosureStatus.DRAFT_AVAILABLE || status == ClosureStatus.REOPENED;
    }

    public void confirmByTeacher(Long confirmerPersonId, LocalDateTime at) {
        this.status = ClosureStatus.TEACHER_CONFIRMED;
        this.confirmedById = confirmerPersonId;
        this.confirmedAt = at;
        this.deliveredAt = at;
    }
}
