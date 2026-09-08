package com.red_escuela.backend.billing.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.ProgramEnrollment;

@Getter
@Setter
@Entity
@Table(name = "scholarship")
public class Scholarship {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_enrollment_id")
    private CourseEnrollment courseEnrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_enrollment_id")
    private ProgramEnrollment programEnrollment;

    @Convert(converter = MonetaryAdjustmentValueConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "value", nullable = false, columnDefinition = "jsonb")
    private MonetaryAdjustmentValue value;

    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ScholarshipStatus status = ScholarshipStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "granted_by_id", nullable = false)
    private Person grantedBy;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @OneToMany(mappedBy = "scholarship", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScholarshipInstallment> installments = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (this.grantedAt == null) {
            this.grantedAt = LocalDateTime.now();
        }
    }

    public void addInstallments(int count) {
        for (int i = 0; i < count; i++) {
            ScholarshipInstallment installment = new ScholarshipInstallment();
            installment.setScholarship(this);
            installments.add(installment);
        }
    }

    public ScholarshipInstallment consumeOne(Invoice invoice) {
        ScholarshipInstallment next = installments.stream()
                .filter(installment -> installment.getStatus() == InstallmentStatus.PENDING)
                .min(Comparator.comparing(ScholarshipInstallment::getId))
                .orElseThrow(() -> new IllegalStateException("Scholarship " + id + " has no PENDING installments"));
        next.markApplied(invoice);
        if (installments.stream().noneMatch(installment -> installment.getStatus() == InstallmentStatus.PENDING)) {
            status = ScholarshipStatus.INACTIVE;
        }
        return next;
    }

    public void cancelRemaining(String reason) {
        installments.stream()
                .filter(installment -> installment.getStatus() == InstallmentStatus.PENDING)
                .forEach(installment -> installment.cancel(reason));
        status = ScholarshipStatus.INACTIVE;
    }
}
