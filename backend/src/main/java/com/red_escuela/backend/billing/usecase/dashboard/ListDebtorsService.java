package com.red_escuela.backend.billing.usecase.dashboard;

import com.red_escuela.backend.billing.service.InvoiceView;
import com.red_escuela.backend.billing.service.InvoiceViewLoader;
import java.time.Clock;
import com.red_escuela.backend.billing.dto.DebtorSummaryResponse;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@UseCase
public class ListDebtorsService implements ListDebtorsUseCase {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceViewLoader invoiceViewLoader;
    private final Clock clock;

    public ListDebtorsService(InvoiceRepository invoiceRepository, InvoiceViewLoader invoiceViewLoader, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceViewLoader = invoiceViewLoader;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DebtorSummaryResponse> list(String sortBy) {
        Map<String, DebtorAccumulator> accumulators = new LinkedHashMap<>();
        for (InvoiceView view : invoiceViewLoader.loadInvoiceViews(invoiceRepository.findByPaymentStatusWithLines(InvoicePaymentStatus.OVERDUE))) {
            Invoice invoice = view.invoice();
            if (view.courseEnrollment() != null) {
                Student student = view.courseEnrollment().getStudent();
                accumulators.computeIfAbsent(student.getId() + "/" + invoice.currency(), key -> new DebtorAccumulator(student, invoice.currency()))
                        .add(view.courseEnrollment().getCourse().getName(), invoice);
            } else if (view.programEnrollment() != null) {
                Student student = view.programEnrollment().getStudent();
                accumulators.computeIfAbsent(student.getId() + "/" + invoice.currency(), key -> new DebtorAccumulator(student, invoice.currency()))
                        .add(view.programEnrollment().getProgram().getName(), invoice);
            }
        }
        Comparator<DebtorSummaryResponse> comparator = "DEBT_AMOUNT".equals(sortBy)
                ? Comparator.<DebtorSummaryResponse, Currency>comparing(row -> row.totalDebt().currency())
                .thenComparing(row -> row.totalDebt().amount(), Comparator.reverseOrder())
                : Comparator.comparing(DebtorSummaryResponse::debtAgeDays, Comparator.reverseOrder());

        return accumulators.values().stream()
                .map(accumulator -> accumulator.toResponse(LocalDate.now(clock)))
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    private static class DebtorAccumulator {
        private final Student student;
        private String primaryEnrollmentName;
        private LocalDate oldestDueDate;
        private Money totalDebt;

        DebtorAccumulator(Student student, Currency currency) {
            this.student = student;
            this.totalDebt = Money.zero(currency);
        }

        void add(String enrollmentName, Invoice invoice) {
            Money remaining = invoice.getAmounts().outstandingAmount().value();
            totalDebt = totalDebt.plus(remaining.isNegative() ? Money.zero(remaining.currency()) : remaining);
            if (oldestDueDate == null || invoice.getDueDate().isBefore(oldestDueDate)) {
                oldestDueDate = invoice.getDueDate();
                primaryEnrollmentName = enrollmentName;
            }
        }

        DebtorSummaryResponse toResponse(LocalDate today) {
            Person person = student.getPerson();
            int debtAgeDays = oldestDueDate != null
                    ? (int) ChronoUnit.DAYS.between(oldestDueDate, today)
                    : 0;
            return new DebtorSummaryResponse(
                    student.getId(),
                    person.getName() + " " + person.getLastname(),
                    person.getDni(),
                    primaryEnrollmentName,
                    totalDebt,
                    debtAgeDays
            );
        }
    }
}
