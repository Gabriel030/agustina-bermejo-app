package com.red_escuela.backend.billing.usecase.dashboard;

import java.time.Clock;
import com.red_escuela.backend.billing.dto.FinancialDashboardResponse;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
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
    private final Clock clock;

    public GetFinancialDashboardService(InvoiceRepository invoiceRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                                        ProgramEnrollmentRepository programEnrollmentRepository,
                                        Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.clock = clock;
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

        YearMonth currentPeriod = YearMonth.now(clock);
        Map<Currency, Money> totalDebt = byCurrency(invoiceRepository.sumOutstandingBalanceByStatus(InvoiceStatus.OVERDUE.name()));
        Map<Currency, Money> monthlyProjected = byCurrency(invoiceRepository.sumAmountDueByPeriod(currentPeriod.atDay(1)));
        Map<Currency, Money> monthlyCollected = byCurrency(invoiceRepository.sumAllocatedByPeriod(currentPeriod.atDay(1)));

        List<FinancialDashboardResponse.CurrencyTotals> totals = Arrays.stream(Currency.values())
                .filter(currency -> currency == Currency.ARS || totalDebt.containsKey(currency)
                        || monthlyProjected.containsKey(currency) || monthlyCollected.containsKey(currency))
                .map(currency -> {
                    Money projected = monthlyProjected.getOrDefault(currency, Money.zero(currency));
                    Money collected = monthlyCollected.getOrDefault(currency, Money.zero(currency));
                    Money debt = projected.minus(collected);
                    return new FinancialDashboardResponse.CurrencyTotals(
                            totalDebt.getOrDefault(currency, Money.zero(currency)), projected, collected,
                            debt.isNegative() ? Money.zero(currency) : debt);
                })
                .toList();

        return new FinancialDashboardResponse(studentsInDebt, studentsUpToDate, totals);
    }

    private static Map<Currency, Money> byCurrency(List<InvoiceRepository.CurrencyTotal> rows) {
        Map<Currency, Money> result = new EnumMap<>(Currency.class);
        for (InvoiceRepository.CurrencyTotal row : rows) {
            Currency currency = Currency.valueOf(row.getCurrency());
            result.put(currency, Money.of(row.getTotal(), currency));
        }
        return result;
    }
}
