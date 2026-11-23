package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.repository.StudentDebtSummaryQuery;
import com.red_escuela.backend.billing.collections.repository.StudentDebtSummaryQuery.StudentDebtRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Service
public class StudentDebtSummaryFinder {

    private final StudentDebtSummaryQuery query;

    public StudentDebtSummaryFinder(StudentDebtSummaryQuery query) {
        this.query = query;
    }

    @Transactional(readOnly = true)
    public List<StudentDebtRow> findByStudentIds(Collection<Long> studentIds, LocalDate today) {
        return query.findByStudentIds(studentIds.stream().distinct().toList(), today);
    }
}
