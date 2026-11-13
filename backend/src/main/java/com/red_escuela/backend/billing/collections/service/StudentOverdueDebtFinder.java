package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.repository.StudentUnpaidDocumentsQuery;
import com.red_escuela.backend.billing.collections.repository.StudentUnpaidDocumentsQuery.StudentUnprotectedUnpaidDocument;
import com.red_escuela.backend.billing.collections.repository.StudentUnpaidDocumentsQuery.UnprotectedUnpaidDocument;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StudentOverdueDebtFinder {

    private final StudentUnpaidDocumentsQuery unpaidDocuments;
    private final PendingReceiptProtectionWindow protectionWindow;
    private final BillingConfigurationReader configurationReader;

    public StudentOverdueDebtFinder(StudentUnpaidDocumentsQuery unpaidDocuments, PendingReceiptProtectionWindow protectionWindow,
                                    BillingConfigurationReader configurationReader) {
        this.unpaidDocuments = unpaidDocuments;
        this.protectionWindow = protectionWindow;
        this.configurationReader = configurationReader;
    }

    @Transactional(readOnly = true)
    public boolean hasOverdueDebt(Long studentId, LocalDate today) {
        Map<LocalDate, Integer> graceByDueDate = new HashMap<>();
        return unpaidDocuments.findUnprotectedDueBefore(studentId, today, protectionWindow.earliestProtectedUploadDateAsOf(today)).stream()
                .anyMatch(document -> isPastGrace(document, today, graceByDueDate));
    }

    @Transactional(readOnly = true)
    public Set<Long> findStudentIdsWithOverdueDebt(Collection<Long> studentIds, LocalDate today) {
        if (studentIds.isEmpty()) {
            return Set.of();
        }
        Map<LocalDate, Integer> graceByDueDate = new HashMap<>();
        return unpaidDocuments.findUnprotectedDueBefore(studentIds, today, protectionWindow.earliestProtectedUploadDateAsOf(today)).stream()
                .filter(entry -> isPastGrace(entry.document(), today, graceByDueDate))
                .map(StudentUnprotectedUnpaidDocument::studentId)
                .collect(Collectors.toSet());
    }

    private boolean isPastGrace(UnprotectedUnpaidDocument document, LocalDate today, Map<LocalDate, Integer> graceByDueDate) {
        if (document.markedOverdue()) {
            return true;
        }
        int grace = graceByDueDate.computeIfAbsent(document.dueDate(), this::gracePeriodDaysOf);
        return today.isAfter(document.dueDate().plusDays(grace));
    }

    private int gracePeriodDaysOf(LocalDate dueDate) {
        return configurationReader.findLatePaymentSettingsEffectiveOn(dueDate)
                .or(configurationReader::findFirstLatePaymentSettings)
                .map(LatePaymentTerms::gracePeriodDays)
                .orElse(0);
    }
}
