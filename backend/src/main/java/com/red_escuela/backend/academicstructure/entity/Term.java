package com.red_escuela.backend.academicstructure.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.academicstructure.exception.InvalidTermDatesException;

@Getter
@Setter
@Entity
@Table(name = "term")
public class Term {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year_number")
    private Integer year;

    @Column(name = "description")
    private String description;

    @Column(name = "type")
    private String type;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType status;

    @Column(name = "enrollment_start")
    private LocalDateTime enrollmentStart;

    @Column(name = "enrollment_end")
    private LocalDateTime enrollmentEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_override")
    private TermEnrollmentOverride enrollmentOverride;

    public void reschedule(LocalDateTime newStartDate, LocalDateTime newEndDate) {
        LocalDateTime start = newStartDate != null ? newStartDate : startDate;
        LocalDateTime end = newEndDate != null ? newEndDate : endDate;
        if (start != null && end != null && end.isBefore(start)) {
            throw new InvalidTermDatesException(start, end);
        }
        startDate = start;
        endDate = end;
    }

    public void defineEnrollmentWindow(LocalDateTime newStart, LocalDateTime newEnd) {
        EnrollmentWindow window = new EnrollmentWindow(
                newStart != null ? newStart : enrollmentStart,
                newEnd != null ? newEnd : enrollmentEnd);
        enrollmentStart = window.start();
        enrollmentEnd = window.end();
    }

    public EnrollmentWindow enrollmentWindow() {
        return new EnrollmentWindow(enrollmentStart, enrollmentEnd);
    }

    public boolean isEnrollmentOpenAt(LocalDateTime moment) {
        return enrollmentWindow().isOpenAt(moment, enrollmentOverride);
    }

    public boolean isEnrollmentClosedByOverride() {
        return enrollmentOverride == TermEnrollmentOverride.CLOSED;
    }

    public boolean contains(YearMonth period) {
        if (startDate == null || endDate == null) {
            return true;
        }
        YearMonth start = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);
        return !period.isBefore(start) && !period.isAfter(end);
    }

    public Long spanInMonths() {
        if (startDate == null || endDate == null) {
            return null;
        }
        return ChronoUnit.MONTHS.between(YearMonth.from(startDate), YearMonth.from(endDate)) + 1;
    }
}
