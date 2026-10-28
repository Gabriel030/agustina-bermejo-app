package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SubjectLockSequence {
    private static final String FIND_LOCK_TARGETS = "select i.student_current_account_id, "
            + "(select min(l.program_enrollment_id) from invoice_line l where l.invoice_id = i.id), "
            + "(select min(l.course_enrollment_id) from invoice_line l where l.invoice_id = i.id) "
            + "from invoice i where i.id = ?";
    private static final String LOCK_PROGRAM_ENROLLMENT = "select id from program_enrollment where id = ? for update";
    private static final String LOCK_COURSE_ENROLLMENT = "select id from course_enrollment where id = ? for update";

    private final JdbcTemplate jdbcTemplate;
    private final StudentCurrentAccountService accountService;
    private final InvoiceLockingService invoiceLockingService;

    public SubjectLockSequence(JdbcTemplate jdbcTemplate, StudentCurrentAccountService accountService, InvoiceLockingService invoiceLockingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accountService = accountService;
        this.invoiceLockingService = invoiceLockingService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Invoice lockInvoiceFollowingTheEnrollmentAndAccount(Long invoiceId) {
        List<LockTargets> targets = jdbcTemplate.query(FIND_LOCK_TARGETS,
                (rs, rowNum) -> new LockTargets(rs.getLong(1), (Long) rs.getObject(2), (Long) rs.getObject(3)), invoiceId);
        targets.forEach(this::lockEnrollmentThenAccount);
        return invoiceLockingService.lockInvoiceForUpdate(invoiceId);
    }

    private void lockEnrollmentThenAccount(LockTargets targets) {
        if (targets.programEnrollmentId() != null) {
            jdbcTemplate.queryForList(LOCK_PROGRAM_ENROLLMENT, Long.class, targets.programEnrollmentId());
        }
        if (targets.courseEnrollmentId() != null) {
            jdbcTemplate.queryForList(LOCK_COURSE_ENROLLMENT, Long.class, targets.courseEnrollmentId());
        }
        accountService.lockStudentCurrentAccount(targets.accountId());
    }

    private record LockTargets(Long accountId, Long programEnrollmentId, Long courseEnrollmentId) {
    }
}
