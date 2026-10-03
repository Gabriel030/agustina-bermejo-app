package com.red_escuela.backend.academicstructure.entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.academicstructure.exception.InvalidRegistrationWindowException;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "course")
public class Course {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseSubject> courseSubjectList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @Column(name = "schedule")
    private String schedule;

    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType isActive;

    @Column(name = "registration_start")
    private LocalDateTime registrationStart;

    @Column(name = "registration_end")
    private LocalDateTime registrationEnd;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "duration_months")
    private Integer durationMonths;

    @Column(name = "waitlist_deadline_offset_days")
    private Integer waitlistDeadlineOffsetDays;

    @Column(name = "visible_in_catalog", nullable = false)
    private boolean visibleInCatalog = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "teaching_mode")
    private TeachingMode teachingMode;

    public boolean changePrice(BigDecimal newPrice) {
        if (newPrice == null || (price != null && price.compareTo(newPrice) == 0)) {
            return false;
        }
        price = newPrice;
        return true;
    }

    public void requireValidRegistrationWindow() {
        if (registrationStart != null && registrationEnd != null && registrationEnd.isBefore(registrationStart)) {
            throw new InvalidRegistrationWindowException(registrationStart, registrationEnd);
        }
    }

    public boolean isRegistrationOpenAt(LocalDateTime moment) {
        return (registrationStart == null || !moment.isBefore(registrationStart))
                && (registrationEnd == null || !moment.isAfter(registrationEnd));
    }

    public boolean hasCapacityFor(long occupiedCount) {
        return capacity == null || occupiedCount < capacity;
    }

    public boolean canGenerateInvoice(YearMonth period, long invoicesGeneratedInWindow) {
        return term.contains(period) && (durationMonths == null || invoicesGeneratedInWindow < durationMonths);
    }

    public Integer remainingInvoices(long invoicesGeneratedInWindow) {
        return durationMonths == null ? null : durationMonths - (int) invoicesGeneratedInWindow;
    }

    public LocalDate waitlistDeadline() {
        return waitlistDeadlineOffsetDays == null ? null : term.getStartDate().toLocalDate().plusDays(waitlistDeadlineOffsetDays);
    }

    public boolean waitlistDeadlineHasPassed(LocalDate today) {
        LocalDate deadline = waitlistDeadline();
        return deadline != null && !today.isBefore(deadline);
    }
}
