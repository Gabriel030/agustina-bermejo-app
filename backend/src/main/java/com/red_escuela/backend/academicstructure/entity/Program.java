package com.red_escuela.backend.academicstructure.entity;

import org.springframework.modulith.NamedInterface;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Money;
import java.util.List;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.exception.CurricularYearOutOfRangeException;

@NamedInterface("model")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "program")
public class Program {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType status;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "enrollment_fee_amount")
    private BigDecimal enrollmentFeeAmount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency = Currency.ARS;

    @Column(name = "total_years")
    private Integer totalYears;

    @Builder.Default
    @Column(name = "total_credits_required", nullable = false)
    private Integer totalCreditsRequired = 0;

    @Column(name = "total_hours_required")
    private Integer totalHoursRequired;

    @Column(name = "visible_in_catalog", nullable = false)
    private boolean visibleInCatalog = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "teaching_mode")
    private TeachingMode teachingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "level")
    private ProgramLevel level;

    @Column(name = "graduation_profile", columnDefinition = "TEXT")
    private String graduationProfile;

    @Column(name = "admission_requirements", columnDefinition = "TEXT")
    private String admissionRequirements;

    @OneToMany(mappedBy = "program", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgramSubject> programSubjectsList;

    public void requireCurricularYearWithinDuration(Integer curricularYear) {
        if (curricularYear == null) {
            return;
        }
        if (curricularYear < 1 || (totalYears != null && curricularYear > totalYears)) {
            throw new CurricularYearOutOfRangeException(curricularYear, totalYears);
        }
    }

    public Money priceAsMoney() {
        return price == null ? null : Money.of(price, currency);
    }

    public Money enrollmentFeeAsMoney() {
        return enrollmentFeeAmount == null ? null : Money.of(enrollmentFeeAmount, currency);
    }

    public boolean changeRecurringFee(Money newPrice) {
        if (newPrice == null) {
            return false;
        }
        requireProgramCurrency(newPrice);
        if (sameAmount(price, newPrice.amount())) {
            return false;
        }
        price = newPrice.amount();
        return true;
    }

    public boolean changeEnrollmentFee(Money newEnrollmentFee) {
        if (newEnrollmentFee == null) {
            return false;
        }
        requireProgramCurrency(newEnrollmentFee);
        if (sameAmount(enrollmentFeeAmount, newEnrollmentFee.amount())) {
            return false;
        }
        enrollmentFeeAmount = newEnrollmentFee.amount();
        return true;
    }

    private void requireProgramCurrency(Money money) {
        if (money.currency() != currency) {
            throw new CurrencyMismatchException(currency, money.currency());
        }
    }

    private static boolean sameAmount(BigDecimal current, BigDecimal requested) {
        return current != null && current.compareTo(requested) == 0;
    }
}
