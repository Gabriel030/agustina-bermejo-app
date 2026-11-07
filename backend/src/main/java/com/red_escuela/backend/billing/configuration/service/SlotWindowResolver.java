package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.planning.domain.SlotWindow;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class SlotWindowResolver {
    private final TermRepository termRepository;
    private final BillingConfigurationReader configurationReader;

    public SlotWindowResolver(TermRepository termRepository, BillingConfigurationReader configurationReader) {
        this.termRepository = termRepository;
        this.configurationReader = configurationReader;
    }

    public Optional<SlotWindow> windowOf(Long programId, YearCycle cycle, int slot, LocalDate today) {
        Set<TermType> irregularTypes = Set.copyOf(configurationReader.irregularTermTypesEffectiveOn(today).types());
        return windowOfAmong(regularTerms(termRepository.findTermsOfProgram(programId), irregularTypes), cycle, slot);
    }

    public static List<Term> regularTerms(List<Term> programTerms, Set<TermType> irregularTypes) {
        return programTerms.stream()
                .filter(t -> t.getEnrollmentStart() != null && t.getStartDate() != null)
                .filter(t -> t.getType() == null || !irregularTypes.contains(t.getType())).toList();
    }

    public static Optional<SlotWindow> windowOfAmong(List<Term> regularTerms, YearCycle cycle, int slot) {
        List<Term> inSlot = regularTerms.stream().filter(t -> cycle.slotContains(slot, YearMonth.from(t.getStartDate()))).toList();
        if (inSlot.isEmpty()) {
            return Optional.empty();
        }
        LocalDate opens = inSlot.stream().map(t -> t.getEnrollmentStart().toLocalDate()).min(LocalDate::compareTo).orElseThrow();
        LocalDate closes = inSlot.stream()
                .map(t -> t.getEnrollmentEnd() != null ? t.getEnrollmentEnd().toLocalDate() : t.getStartDate().toLocalDate())
                .min(LocalDate::compareTo).orElseThrow();
        return Optional.of(new SlotWindow(opens, closes));
    }
}
