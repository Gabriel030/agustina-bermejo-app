package com.red_escuela.backend.billing.usecase.dashboard;

import com.red_escuela.backend.billing.dto.FinancialDashboardResponse;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@UseCase
public class GetFinancialDashboardService implements GetFinancialDashboardUseCase {

    private final InvoiceRepository invoiceRepository;
    private final StudentRepository studentRepository;

    public GetFinancialDashboardService(InvoiceRepository invoiceRepository, StudentRepository studentRepository) {
        this.invoiceRepository = invoiceRepository;
        this.studentRepository = studentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialDashboardResponse execute() {
        Set<Long> debtorIds = new HashSet<>(invoiceRepository.findCourseStudentIdsByStatus(InvoiceStatus.OVERDUE));
        debtorIds.addAll(invoiceRepository.findProgramStudentIdsByStatus(InvoiceStatus.OVERDUE));
        int studentsInDebt = debtorIds.size();

        long totalStudents = studentRepository.count();
        int studentsUpToDate = (int) Math.max(0, totalStudents - studentsInDebt);

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
        BigDecimal monthlyProjected = invoiceRepository.sumBaseAmountByPeriod(currentPeriod);
        BigDecimal monthlyCollected = invoiceRepository.sumBaseAmountByPeriodAndStatusIn(
                currentPeriod, List.of(InvoiceStatus.PAID, InvoiceStatus.WAIVED));
        BigDecimal monthlyDebt = monthlyProjected.subtract(monthlyCollected).max(BigDecimal.ZERO);

        return new FinancialDashboardResponse(
                studentsInDebt, studentsUpToDate, totalDebt,
                monthlyProjected, monthlyCollected, monthlyDebt
        );
    }
}
