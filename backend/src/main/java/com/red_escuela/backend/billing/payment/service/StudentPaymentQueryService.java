package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.identity.api.StudentExistenceChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StudentPaymentQueryService {

    private final StudentExistenceChecker students;
    private final StudentCurrentAccountService accountService;
    private final PaymentRepository payments;

    public StudentPaymentQueryService(StudentExistenceChecker students, StudentCurrentAccountService accountService, PaymentRepository payments) {
        this.students = students;
        this.accountService = accountService;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public List<Payment> findPaymentsOfExistingStudent(Long studentId) {
        students.requireExistingStudent(studentId);
        return findPaymentsOfStudent(studentId);
    }

    @Transactional(readOnly = true)
    public List<Payment> findPaymentsOfStudent(Long studentId) {
        List<Long> accountIds = accountService.findStudentCurrentAccountIds(studentId);
        return accountIds.isEmpty() ? List.of() : payments.findByStudentCurrentAccountIdInOrderByRegisteredAtDescIdDesc(accountIds);
    }
}
