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
@Table(name = "commission_closure_reminder")
public class CommissionClosureReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_kind", nullable = false)
    private SectionKind sectionKind;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "days_after_term_end", nullable = false)
    private int daysAfterTermEnd;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
