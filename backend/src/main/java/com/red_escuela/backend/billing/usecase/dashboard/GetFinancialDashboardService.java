package com.red_escuela.backend.billing.usecase.dashboard;

import com.red_escuela.backend.billing.dto.FinancialDashboardResponse;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@UseCase
public class GetFinancialDashboardService implements GetFinancialDashboardUseCase {
    private static final List<EnrollmentStatus> ACTIVE_COURSE_STATUSES = List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS);
    private static final List<ProgramEnrollmentStatus> ACTIVE_PROGRAM_STATUSES = List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED);

    private final InvoiceRepository invoiceRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;

    public GetFinancialDashboardService(InvoiceRepository invoiceRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                                        ProgramEnrollmentRepository programEnrollmentRepository) {
        this.invoiceRepository = invoiceRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialDashboardResponse execute() {
        Set<Long> debtorIds = new HashSet<>(invoiceRepository.findCourseStudentIdsByStatus(InvoiceStatus.OVERDUE));
        debtorIds.addAll(invoiceRepository.findProgramStudentIdsByStatus(InvoiceStatus.OVERDUE));
        int studentsInDebt = debtorIds.size();

        Set<Long> activeStudentIds = new HashSet<>(courseEnrollmentRepository.findDistinctStudentIdsByStatusIn(ACTIVE_COURSE_STATUSES));
        activeStudentIds.addAll(programEnrollmentRepository.findDistinctStudentIdsByStatusIn(ACTIVE_PROGRAM_STATUSES));
        activeStudentIds.removeAll(debtorIds);
        int studentsUpToDate = activeStudentIds.size();

        BigDecimal totalDebt = invoiceRepository.sumOutstandingBalanceByStatus(InvoiceStatus.OVERDUE.name());

        YearMonth currentPeriod = YearMonth.now();
        BigDecimal monthlyProjected = invoiceRepository.sumAmountDueByPeriod(currentPeriod.atDay(1));
        BigDecimal monthlyCollected = invoiceRepository.sumAllocatedByPeriod(currentPeriod);
        BigDecimal monthlyDebt = monthlyProjected.subtract(monthlyCollected).max(BigDecimal.ZERO);

        return new FinancialDashboardResponse(
                studentsInDebt, studentsUpToDate, totalDebt,
                monthlyProjected, monthlyCollected, monthlyDebt
        );
    }
}
