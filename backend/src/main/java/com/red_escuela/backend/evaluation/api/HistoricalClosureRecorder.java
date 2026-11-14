package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.evaluation.entity.ClosureFinalCondition;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.CommissionClosureStudent;
import com.red_escuela.backend.evaluation.exception.HistoricalClosureMissingException;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureStudentRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.shared.domain.Grade;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HistoricalClosureRecorder {
    private static final String UNKNOWN_ACTA_DATA = "S/D";

    private final CommissionClosureRepository closureRepository;
    private final CommissionClosureStudentRepository closureStudentRepository;
    private final ClosureReportNumberAllocator reportNumberAllocator;
    private final CurrentActorFinder currentActorFinder;

    public HistoricalClosureRecorder(CommissionClosureRepository closureRepository,
            CommissionClosureStudentRepository closureStudentRepository,
            ClosureReportNumberAllocator reportNumberAllocator, CurrentActorFinder currentActorFinder) {
        this.closureRepository = closureRepository;
        this.closureStudentRepository = closureStudentRepository;
        this.reportNumberAllocator = reportNumberAllocator;
        this.currentActorFinder = currentActorFinder;
    }

    @Transactional(readOnly = true)
    public boolean reportNumberExists(String reportNumber) {
        return closureRepository.existsByReportNumber(reportNumber);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Long recordClosure(HistoricalClosure historical) {
        String reportNumber = historical.reportNumber().isEmpty() ? reportNumberAllocator.allocateNextReportNumber()
                : historical.reportNumber();
        CommissionClosure closure = CommissionClosure.builder().sectionKind(SectionKind.PROGRAM)
                .sectionId(historical.sectionId()).status(ClosureStatus.CLOSED).reportNumber(reportNumber)
                .book(orUnknown(historical.book())).folio(orUnknown(historical.folio()))
                .closedById(currentActorFinder.currentPersonId())
                .closedAt(historical.closedOn().atTime(LocalTime.NOON)).closedWithoutTeacherConfirmation(true)
                .reportVersion(1).build();
        return closureRepository.save(closure).getId();
    }

    @Transactional
    public Optional<Long> recordStudentResult(Long sectionId, Long enrollmentId, EnrollmentStatus status,
            FinalGradeType gradeType, BigDecimal score) {
        Optional<ClosureFinalCondition> condition = conditionOf(status, gradeType);
        if (condition.isEmpty()) {
            return Optional.empty();
        }
        CommissionClosure closure = closureRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, sectionId)
                .orElseThrow(() -> new HistoricalClosureMissingException(sectionId));
        boolean promotes = condition.get() == ClosureFinalCondition.PROMOTES;
        return Optional.of(closureStudentRepository.save(CommissionClosureStudent.builder().closureId(closure.getId())
                .enrollmentId(enrollmentId).finalCondition(condition.get())
                .promotionGrade(promotes ? Grade.ofNullable(score) : null).build()).getId());
    }

    private static Optional<ClosureFinalCondition> conditionOf(EnrollmentStatus status, FinalGradeType gradeType) {
        return switch (status) {
            case APPROVED -> Optional.of(gradeType == FinalGradeType.PROMOTION ? ClosureFinalCondition.PROMOTES
                    : ClosureFinalCondition.GOES_TO_FINAL);
            case REGULAR -> Optional.of(ClosureFinalCondition.GOES_TO_FINAL);
            case FAILED -> Optional.of(ClosureFinalCondition.FAILED);
            default -> Optional.empty();
        };
    }

    private static String orUnknown(String value) {
        return value.isEmpty() ? UNKNOWN_ACTA_DATA : value;
    }
}
