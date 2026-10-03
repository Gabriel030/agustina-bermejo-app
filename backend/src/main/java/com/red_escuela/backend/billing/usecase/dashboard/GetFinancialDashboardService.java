package com.red_escuela.backend.billing.usecase.dashboard;

import com.red_escuela.backend.billing.dto.FinancialDashboardResponse;
import com.red_escuela.backend.billing.entity.Invoice;
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
import java.util.*;
import java.util.stream.Collectors;

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

        List<InvoiceStatus> debtStatuses = List.of(InvoiceStatus.OVERDUE);
        List<Invoice> debtInvoices = invoiceRepository.findByStatusInWithAdjustments(debtStatuses);

        Map<Long, BigDecimal> allocatedByInvoice;
        if (debtInvoices.isEmpty()) {
            allocatedByInvoice = Map.of();
        } else {
            List<Long> invoiceIds = debtInvoices.stream().map(Invoice::getId).toList();
            allocatedByInvoice = invoiceRepository.sumAllocatedByInvoiceIds(invoiceIds).stream()
                    .collect(Collectors.toMap(row -> (Long) row[0], row -> (BigDecimal) row[1]));
        }

        Map<Long, BigDecimal> allocated = allocatedByInvoice;
        BigDecimal totalDebt = debtInvoices.stream()
                .map(inv -> inv.getAmountDue()
                        .subtract(allocated.getOrDefault(inv.getId(), BigDecimal.ZERO))
                        .max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        YearMonth currentPeriod = YearMonth.now();
        BigDecimal monthlyProjected = invoiceRepository.findByPeriodNotCancelledWithAdjustments(currentPeriod).stream()
                .map(Invoice::getAmountDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthlyCollected = invoiceRepository.sumAllocatedByPeriod(currentPeriod);
        BigDecimal monthlyDebt = monthlyProjected.subtract(monthlyCollected).max(BigDecimal.ZERO);

        return new FinancialDashboardResponse(
                studentsInDebt, studentsUpToDate, totalDebt,
                monthlyProjected, monthlyCollected, monthlyDebt
        );
    }
}
