package com.red_escuela.backend.billing.usecase.dashboard;

import java.time.Clock;
import com.red_escuela.backend.billing.dto.DebtorSummaryResponse;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
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
    private final Clock clock;

    public ListDebtorsService(InvoiceRepository invoiceRepository, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DebtorSummaryResponse> list(String sortBy) {
        List<InvoiceStatus> debtStatuses = List.of(InvoiceStatus.OVERDUE);

        List<Invoice> courseInvoices = invoiceRepository.findByCourseEnrollmentStatusInWithStudent(debtStatuses);
        List<Invoice> programInvoices = invoiceRepository.findByProgramEnrollmentStatusInWithStudent(debtStatuses);

        Map<String, DebtorAccumulator> accumulators = new LinkedHashMap<>();

        for (Invoice invoice : courseInvoices) {
            Student student = invoice.getCourseEnrollment().getStudent();
            String name = invoice.getCourseEnrollment().getCourse().getName();
            accumulators.computeIfAbsent(student.getId() + "/" + invoice.currency(), key -> new DebtorAccumulator(student, invoice.currency())).add(name, invoice);
        }
        for (Invoice invoice : programInvoices) {
            Student student = invoice.getProgramEnrollment().getStudent();
            String name = invoice.getProgramEnrollment().getProgram().getName();
            accumulators.computeIfAbsent(student.getId() + "/" + invoice.currency(), key -> new DebtorAccumulator(student, invoice.currency())).add(name, invoice);
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
            Money remaining = invoice.getRemainingBalance();
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
