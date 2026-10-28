package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.domain.RefundRequestGroup;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.identity.api.StudentExistenceLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Transactional(readOnly = true)
    public List<RefundRequestGroup> findRefundRequestsOfExistingStudent(Long studentId) {
        students.requireExistingStudent(studentId);
        return findRefundRequestsOfStudent(studentId);
    }

    @Transactional(readOnly = true)
    public List<RefundRequestGroup> findRefundRequestsOfStudent(Long studentId) {
        Map<Long, List<Refund>> partsByRequest = new LinkedHashMap<>();
        findRefundsOfStudent(studentId).forEach(refund -> partsByRequest.computeIfAbsent(refund.getRefundRequestId(), id -> new ArrayList<>()).add(refund));
        return partsByRequest.entrySet().stream()
                .map(entry -> groupOf(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static RefundRequestGroup groupOf(Long refundRequestId, List<Refund> parts) {
        List<Refund> ordered = parts.stream().sorted(Comparator.comparing(Refund::getId)).toList();
        Refund first = ordered.get(0);
        return new RefundRequestGroup(refundRequestId, first.getAuthorizationRequestId(), first.getRequestedOn(), ordered);
    }
}
