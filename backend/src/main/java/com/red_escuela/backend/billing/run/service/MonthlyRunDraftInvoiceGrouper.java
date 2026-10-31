package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import com.red_escuela.backend.billing.run.domain.DraftInvoiceGroup;
import com.red_escuela.backend.billing.run.domain.DraftInvoiceGroupKey;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceEnrollmentType;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.repository.DraftInvoiceLineRepository;
import com.red_escuela.backend.enrollment.api.EnrollmentNameLookup;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MonthlyRunDraftInvoiceGrouper {
    private final DraftInvoiceLineRepository lineRepository;
    private final EnrollmentNameLookup enrollmentNames;

    public MonthlyRunDraftInvoiceGrouper(DraftInvoiceLineRepository lineRepository, EnrollmentNameLookup enrollmentNames) {
        this.lineRepository = lineRepository;
        this.enrollmentNames = enrollmentNames;
    }

    @Transactional(readOnly = true)
    public List<DraftInvoiceGroup> groupDraftsOfRunComparedWithPreviousPeriod(MonthlyInvoiceRun run) {
        List<InvoiceLine> draftLines = lineRepository.findEnrollmentLinesOfDraftInvoicesOfRun(run.getId());
        if (draftLines.isEmpty()) {
            return List.of();
        }
        List<InvoiceLine> previousLines = lineRepository.findEnrollmentLinesOfIssuedInvoicesOfPeriod(YearMonth.from(run.getPeriod()).minusMonths(1));
        Names names = namesOf(draftLines, previousLines);
        Map<DraftInvoiceGroupKey, Totals> draftTotals = totalsByGroup(draftLines, names);
        Map<DraftInvoiceGroupKey, Totals> previousTotals = totalsByGroup(previousLines, names);
        return draftTotals.entrySet().stream()
                .map(entry -> new DraftInvoiceGroup(entry.getKey(), entry.getValue().invoiceIds.size(), entry.getValue().total,
                        previousTotals.containsKey(entry.getKey()) ? previousTotals.get(entry.getKey()).total : null))
                .sorted(Comparator.comparing((DraftInvoiceGroup group) -> group.key().scope()).thenComparing(group -> group.key().name()).thenComparing(group -> group.key().feeType()))
                .toList();
    }

    private Names namesOf(List<InvoiceLine> draftLines, List<InvoiceLine> previousLines) {
        Set<Long> programEnrollmentIds = new HashSet<>();
        Set<Long> courseEnrollmentIds = new HashSet<>();
        for (List<InvoiceLine> lines : List.of(draftLines, previousLines)) {
            for (InvoiceLine line : lines) {
                if (line.getProgramEnrollmentId() != null) {
                    programEnrollmentIds.add(line.getProgramEnrollmentId());
                } else {
                    courseEnrollmentIds.add(line.getCourseEnrollmentId());
                }
            }
        }
        return new Names(enrollmentNames.findProgramNamesByProgramEnrollmentId(programEnrollmentIds), enrollmentNames.findCourseNamesByCourseEnrollmentId(courseEnrollmentIds));
    }

    private Map<DraftInvoiceGroupKey, Totals> totalsByGroup(List<InvoiceLine> lines, Names names) {
        Map<DraftInvoiceGroupKey, Totals> totals = new LinkedHashMap<>();
        for (InvoiceLine line : lines) {
            Money net = line.calculateNetAmount().value();
            totals.computeIfAbsent(keyOf(line, names), key -> new Totals(Money.zero(key.currency()))).add(line.getInvoice().getId(), net);
        }
        return totals;
    }

    private DraftInvoiceGroupKey keyOf(InvoiceLine line, Names names) {
        boolean isProgramLine = line.getProgramEnrollmentId() != null;
        Long enrollmentId = isProgramLine ? line.getProgramEnrollmentId() : line.getCourseEnrollmentId();
        Map<Long, String> nameById = isProgramLine ? names.programNames : names.courseNames;
        return new DraftInvoiceGroupKey(isProgramLine ? MonthlyInvoiceEnrollmentType.PROGRAM : MonthlyInvoiceEnrollmentType.COURSE,
                nameById.getOrDefault(enrollmentId, String.valueOf(enrollmentId)), line.getFeeType(), line.getCurrency());
    }

    private record Names(Map<Long, String> programNames, Map<Long, String> courseNames) {
    }

    private static final class Totals {
        private final Set<Long> invoiceIds = new HashSet<>();
        private Money total;

        private Totals(Money zero) {
            this.total = zero;
        }

        private void add(Long invoiceId, Money amount) {
            invoiceIds.add(invoiceId);
            total = total.plus(amount);
        }
    }
}
