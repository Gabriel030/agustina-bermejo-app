package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.LabeledStudentDiscount;
import com.red_escuela.backend.billing.discount.service.StudentDiscountsOfStudentReader;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class ListStudentDiscountsOfStudentService implements ListStudentDiscountsOfStudentUseCase {
    private final StudentDiscountsOfStudentReader reader;

    public ListStudentDiscountsOfStudentService(StudentDiscountsOfStudentReader reader) {
        this.reader = reader;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<LabeledStudentDiscount> listStudentDiscountsOfStudent(Long studentId) {
        return reader.readDiscountsOfExistingStudent(studentId);
    }
}
