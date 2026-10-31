package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.LabeledStudentDiscount;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.identity.api.StudentExistenceChecker;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StudentDiscountsOfStudentReader {
    private final StudentExistenceChecker students;
    private final StudentDiscountRepository repository;
    private final LabeledStudentDiscountReader labeledReader;

    public StudentDiscountsOfStudentReader(StudentExistenceChecker students, StudentDiscountRepository repository, LabeledStudentDiscountReader labeledReader) {
        this.students = students;
        this.repository = repository;
        this.labeledReader = labeledReader;
    }

    public List<LabeledStudentDiscount> readDiscountsOfExistingStudent(Long studentId) {
        students.requireExistingStudent(studentId);
        return labeledReader.readLabeled(repository.findByStudentIdOrderByIdDesc(studentId), true);
    }
}
