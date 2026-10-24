package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.identity.api.StudentExistenceLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StudentRefundQueryService {

    private final StudentExistenceLookup students;
    private final StudentCurrentAccountService accountService;
    private final RefundRepository refunds;

    public StudentRefundQueryService(StudentExistenceLookup students, StudentCurrentAccountService accountService, RefundRepository refunds) {
        this.students = students;
        this.accountService = accountService;
        this.refunds = refunds;
    }

    @Transactional(readOnly = true)
    public List<Refund> findRefundsOfExistingStudent(Long studentId) {
        students.requireExistingStudent(studentId);
        return findRefundsOfStudent(studentId);
    }

    @Transactional(readOnly = true)
    public List<Refund> findRefundsOfStudent(Long studentId) {
        List<Long> accountIds = accountService.findStudentCurrentAccountIds(studentId);
        return accountIds.isEmpty() ? List.of() : refunds.findByStudentCurrentAccountIdInOrderByRequestedOnDescIdDesc(accountIds);
    }
}
