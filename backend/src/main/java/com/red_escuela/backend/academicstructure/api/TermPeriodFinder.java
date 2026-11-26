package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.api.TermNotFoundException;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class TermPeriodFinder {

    private final TermRepository termRepository;

    public TermPeriodFinder(TermRepository termRepository) {
        this.termRepository = termRepository;
    }

    public TermPeriodView findPeriod(Long termId) {
        Term term = termRepository.findById(termId).orElseThrow(() -> new TermNotFoundException(termId));
        return new TermPeriodView(term.getId(), term.getStartDate(), term.getEndDate());
    }

    public Map<Long, TermPeriodView> findPeriods(Collection<Long> termIds) {
        if (termIds.isEmpty()) {
            return Map.of();
        }
        return termRepository.findAllById(termIds).stream()
                .map(term -> new TermPeriodView(term.getId(), term.getStartDate(), term.getEndDate()))
                .collect(Collectors.toMap(TermPeriodView::termId, Function.identity()));
    }
}
