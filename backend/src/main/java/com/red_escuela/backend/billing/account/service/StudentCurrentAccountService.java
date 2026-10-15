package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.billing.account.domain.CurrentAccountEntry;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.repository.CurrentAccountEntryRepository;
import com.red_escuela.backend.billing.account.repository.StudentCurrentAccountRepository;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StudentCurrentAccountService {

    private static final Object LOCKED_ACCOUNT_IDS_RESOURCE = new Object();

    private final StudentCurrentAccountRepository accountRepository;
    private final CurrentAccountEntryRepository entryRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public StudentCurrentAccount findOrOpenAndLockStudentCurrentAccount(Long studentId, Currency currency) {
        accountRepository.insertStudentCurrentAccountIfAbsent(studentId, currency.name(), ApplicationTime.now());
        Long accountId = accountRepository.findIdByStudentIdAndCurrency(studentId, currency).orElseThrow();
        return lockStudentCurrentAccount(accountId);
    }

    @Transactional(readOnly = true)
    public Optional<StudentCurrentAccount> findStudentCurrentAccount(Long studentId, Currency currency) {
        return accountRepository.findIdByStudentIdAndCurrency(studentId, currency).flatMap(accountRepository::findById);
    }

    @Transactional(readOnly = true)
    public List<Long> findStudentCurrentAccountIds(Long studentId) {
        return accountRepository.findIdsByStudentId(studentId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StudentCurrentAccount lockStudentCurrentAccount(Long studentCurrentAccountId) {
        StudentCurrentAccount account = accountRepository.findByIdForUpdate(studentCurrentAccountId).orElseThrow();
        entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
        lockedAccountIdsOfCurrentTransaction().add(account.getId());
        return account;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CurrentAccountEntry> recordInvoiceIssuedDebit(StudentCurrentAccount lockedAccount, Invoice issuedInvoice, LocalDate effectiveDate) {
        requireLocked(lockedAccount);
        return persist(lockedAccount, lockedAccount.recordInvoiceIssuedDebit(issuedInvoice, effectiveDate));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CurrentAccountEntry> recordDebitNoteIssuedDebit(StudentCurrentAccount lockedAccount, Invoice issuedDebitNote, LocalDate effectiveDate) {
        requireLocked(lockedAccount);
        return persist(lockedAccount, lockedAccount.recordDebitNoteIssuedDebit(issuedDebitNote, effectiveDate));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CurrentAccountEntry recordPaymentReceivedCredit(StudentCurrentAccount lockedAccount, Long paymentId, NonNegativeMoney amount,
                                                           LocalDate paidOn, String concept) {
        requireLocked(lockedAccount);
        return persist(lockedAccount, lockedAccount.recordPaymentReceivedCredit(paymentId, amount, paidOn, concept));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void consumeUnallocatedCreditForAllocation(StudentCurrentAccount lockedAccount, NonNegativeMoney amount) {
        requireLocked(lockedAccount);
        lockedAccount.consumeUnallocatedCreditForAllocation(amount);
        accountRepository.save(lockedAccount);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreUnallocatedCreditFromReversedAllocation(StudentCurrentAccount lockedAccount, NonNegativeMoney amount) {
        requireLocked(lockedAccount);
        lockedAccount.restoreUnallocatedCreditFromReversedAllocation(amount);
        accountRepository.save(lockedAccount);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CurrentAccountEntry recordCreditNoteIssuedCredit(StudentCurrentAccount lockedAccount, Long creditNoteId, NonNegativeMoney offsetAmount,
                                                            NonNegativeMoney freeCreditAmount, LocalDate effectiveDate, String concept) {
        requireLocked(lockedAccount);
        return persist(lockedAccount, lockedAccount.recordCreditNoteIssuedCredit(creditNoteId, offsetAmount, freeCreditAmount, effectiveDate, concept));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CurrentAccountEntry recordPaymentReversalDebit(StudentCurrentAccount lockedAccount, CurrentAccountEntry originalPaymentCredit,
                                                          LocalDate effectiveDate, String reason) {
        requireLocked(lockedAccount);
        if (originalPaymentCredit.getId() != null && entryRepository.existsByCompensatesEntryId(originalPaymentCredit.getId())) {
            lockedAccount.rememberReversedCreditEntry(originalPaymentCredit.getId());
        }
        return persist(lockedAccount, lockedAccount.recordPaymentReversalDebit(originalPaymentCredit, effectiveDate, reason));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CurrentAccountEntry recordRefundPaidDebit(StudentCurrentAccount lockedAccount, Long refundId, NonNegativeMoney amount,
                                                     LocalDate effectiveDate, String concept) {
        requireLocked(lockedAccount);
        return persist(lockedAccount, lockedAccount.recordRefundPaidDebit(refundId, amount, effectiveDate, concept));
    }

    public boolean isCreditNoteBookedInLedger(Long creditNoteId) {
        return entryRepository.existsByCreditNoteId(creditNoteId);
    }

    private void requireLocked(StudentCurrentAccount account) {
        if (!entityManager.contains(account) || !lockedAccountIdsOfCurrentTransaction().contains(account.getId())) {
            throw new IllegalStateException("The student current account must be locked in the current transaction before recording an entry");
        }
    }

    private Set<Long> lockedAccountIdsOfCurrentTransaction() {
        @SuppressWarnings("unchecked")
        Set<Long> lockedIds = (Set<Long>) TransactionSynchronizationManager.getResource(LOCKED_ACCOUNT_IDS_RESOURCE);
        if (lockedIds != null) {
            return lockedIds;
        }
        Set<Long> created = new HashSet<>();
        TransactionSynchronizationManager.bindResource(LOCKED_ACCOUNT_IDS_RESOURCE, created);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void suspend() {
                TransactionSynchronizationManager.unbindResourceIfPossible(LOCKED_ACCOUNT_IDS_RESOURCE);
            }

            @Override
            public void resume() {
                TransactionSynchronizationManager.bindResource(LOCKED_ACCOUNT_IDS_RESOURCE, created);
            }

            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(LOCKED_ACCOUNT_IDS_RESOURCE);
            }
        });
        return created;
    }

    private CurrentAccountEntry persist(StudentCurrentAccount account, CurrentAccountEntry entry) {
        accountRepository.save(account);
        return entryRepository.save(entry);
    }

    private Optional<CurrentAccountEntry> persist(StudentCurrentAccount account, Optional<CurrentAccountEntry> entry) {
        entry.ifPresent(recorded -> persist(account, recorded));
        return entry;
    }
}
