package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.account.domain.CurrentAccountBalanceSide;
import com.red_escuela.backend.billing.account.domain.CurrentAccountEntry;
import com.red_escuela.backend.billing.account.domain.CurrentAccountEntryType;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.exception.CurrentAccountEntrySourceMismatchException;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.CreditNoteApplicationKind;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceCreditNoteApplication;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineCredit;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.time.ApplicationTime;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-139")
@Tag("D-136")
@Tag("D-159")
@Tag("D-127")
@Tag("D-145")
@Tag("D-097")
@Tag("D-094")
class StudentCurrentAccountServiceIT extends AbstractCurrentAccountIT {

    private static final LocalDate DAY = LocalDate.of(2027, 3, 10);

    @PersistenceContext
    private EntityManager entityManager;

    private BillingInvariantChecker invariants;

    @BeforeEach
    void startBillingInvariantBaseline() {
        invariants = BillingInvariantChecker.startingNow(jdbc);
    }

    @Test
    @Tag("D-144")
    void theInvariantCheckerFailsWhenTheUnallocatedCreditIsCorrupted() {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        Long invoiceId = newIssuedInvoiceId(accountId, Currency.ARS, "80000.00");
        inTransaction(() -> {
            StudentCurrentAccount account = service.lockStudentCurrentAccount(accountId);
            service.recordInvoiceIssuedDebit(account, invoiceRepository.findById(invoiceId).orElseThrow(), DAY);
            service.recordPaymentReceivedCredit(account, BillingTestFixtures.seedApprovedPaymentRow(account.getId(), ars("30000.00")), ars("30000.00"), DAY, "Pago");
            return null;
        });
        invariants.assertAllBillingInvariantsHold();

        jdbc.update("update student_current_account set unallocated_credit_amount = unallocated_credit_amount + 1 where id = ?", accountId);

        assertThatThrownBy(invariants::assertAllBillingInvariantsHold)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("I-19 balance differs from unallocated credit minus outstanding")
                .hasMessageContaining("account " + accountId);
    }

    @Test
    void insufficientMoneyStaysAsCreditAndTheInvoiceStaysPending() {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        Long invoiceId = newIssuedInvoiceId(accountId, Currency.ARS, "80000.00");

        inTransaction(() -> {
            StudentCurrentAccount account = service.lockStudentCurrentAccount(accountId);
            Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
            service.recordInvoiceIssuedDebit(account, invoice, DAY);
            service.recordPaymentReceivedCredit(account, BillingTestFixtures.seedApprovedPaymentRow(account.getId(), ars("30000.00")), ars("30000.00"), DAY, "Pago");
            return null;
        });

        StudentCurrentAccount account = loadAccount(accountId);
        assertThat(account.getUnallocatedCreditAmount()).isEqualTo(ars("30000.00"));
        assertThat(account.getBalance().amount()).isEqualTo(ars("50000.00"));
        assertThat(account.getBalance().side()).isEqualTo(CurrentAccountBalanceSide.OWES);
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId)).isEqualTo("PENDING");
        assertThat(amount("select outstanding_amount from invoice where id = ?", invoiceId)).isEqualByComparingTo("80000.00");
        assertThat(entryRepository.findByStudentCurrentAccountIdOrderBySequenceNumberAsc(accountId)).hasSize(2);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aCreditNoteWithFreeCreditRaisesTheUnallocatedCreditAndACancellationKeepsIt() {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        Long invoiceId = newIssuedInvoiceId(accountId, Currency.ARS, "80000.00");
        inTransaction(() -> {
            StudentCurrentAccount account = service.lockStudentCurrentAccount(accountId);
            service.recordInvoiceIssuedDebit(account, invoiceRepository.findById(invoiceId).orElseThrow(), DAY);
            service.recordCreditNoteIssuedCredit(account, newCreditNoteId(accountId, invoiceId, "0.00", "40000.00"), ars("0.00"), ars("40000.00"), DAY, "Nota de crédito");
            return null;
        });
        assertThat(loadAccount(accountId).getUnallocatedCreditAmount()).isEqualTo(ars("40000.00"));
        assertThat(loadAccount(accountId).getBalance().amount()).isEqualTo(ars("40000.00"));
        assertThat(loadAccount(accountId).getBalance().side()).isEqualTo(CurrentAccountBalanceSide.OWES);
        assertThat(loadAccount(accountId).calculateSignedBalance().amount()).isEqualByComparingTo("-40000.00");

        Long cancelledId = newIssuedInvoiceId(accountId, Currency.ARS, "80000.00");
        inTransaction(() -> {
            StudentCurrentAccount account = service.lockStudentCurrentAccount(accountId);
            Invoice invoice = invoiceRepository.findInvoiceWithLinesById(cancelledId).orElseThrow();
            service.recordInvoiceIssuedDebit(account, invoice, DAY);
            invoice.applyCreditNote(new InvoiceCreditNoteApplication(CreditNoteApplicationKind.CANCELLATION, ars("80000.00"), ars("0.00"),
                    List.of(new InvoiceLineCredit(1, ars("80000.00")))), DAY, ACTOR);
            service.recordCreditNoteIssuedCredit(account, newCreditNoteId(accountId, cancelledId, "80000.00", "0.00"), ars("80000.00"), ars("0.00"), DAY, "Cancelación");
            return null;
        });

        assertThat(loadAccount(accountId).getUnallocatedCreditAmount()).isEqualTo(ars("40000.00"));
        assertThat(loadAccount(accountId).calculateSignedBalance().amount()).isEqualByComparingTo("-40000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aStudentWithTwoCurrenciesHasExactlyTwoAccountsAndTheSameCurrencyInvoicesShareOne() {
        Long studentId = newStudentId();
        Long arsAccountId = newAccountId(studentId, Currency.ARS);
        Long usdAccountId = newAccountId(studentId, Currency.USD);
        Long sameArsAccountId = newAccountId(studentId, Currency.ARS);
        Long careerInvoice = newIssuedInvoiceId(arsAccountId, Currency.ARS, "1000.00");
        Long courseInvoice = newIssuedInvoiceId(arsAccountId, Currency.ARS, "250.50");
        Long usdInvoice = newIssuedInvoiceId(usdAccountId, Currency.USD, "30.00");

        inTransaction(() -> {
            StudentCurrentAccount ars = service.findOrOpenAndLockStudentCurrentAccount(studentId, Currency.ARS);
            service.recordInvoiceIssuedDebit(ars, invoiceRepository.findById(careerInvoice).orElseThrow(), DAY);
            service.recordInvoiceIssuedDebit(ars, invoiceRepository.findById(courseInvoice).orElseThrow(), DAY);
            StudentCurrentAccount usd = service.findOrOpenAndLockStudentCurrentAccount(studentId, Currency.USD);
            service.recordInvoiceIssuedDebit(usd, invoiceRepository.findById(usdInvoice).orElseThrow(), DAY);
            return null;
        });

        assertThat(sameArsAccountId).isEqualTo(arsAccountId);
        assertThat(jdbc.queryForObject("select count(*) from student_current_account where student_id = ?", Integer.class, studentId)).isEqualTo(2);
        assertThat(loadAccount(arsAccountId).getBalance().amount()).isEqualTo(ars("1250.50"));
        assertThat(loadAccount(usdAccountId).getBalance().amount()).isEqualTo(com.red_escuela.backend.billing.account.domain.AccountTestData.usd("30.00"));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void gabrielExampleIsPersistedWithTheReversalCompensatingTheCredit() {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        Long invoiceId = newIssuedInvoiceId(accountId, Currency.ARS, "1100000.00");
        Long paymentEntryId = inTransaction(() -> {
            StudentCurrentAccount account = service.lockStudentCurrentAccount(accountId);
            service.recordInvoiceIssuedDebit(account, invoiceRepository.findById(invoiceId).orElseThrow(), DAY);
            return service.recordPaymentReceivedCredit(account, BillingTestFixtures.seedApprovedPaymentRow(account.getId(), ars("1200000.00")), ars("1200000.00"), DAY, "Pago").getId();
        });
        assertThat(loadAccount(accountId).calculateSignedBalance().amount()).isEqualByComparingTo("100000.00");

        inTransaction(() -> {
            StudentCurrentAccount account = service.lockStudentCurrentAccount(accountId);
            CurrentAccountEntry payment = entryRepository.findById(paymentEntryId).orElseThrow();
            CurrentAccountEntry reversal = service.recordPaymentReversalDebit(account, payment, DAY, "Contracargo");
            assertThat(reversal.getCompensatesEntryId()).isEqualTo(paymentEntryId);
            return null;
        });
        assertThat(loadAccount(accountId).calculateSignedBalance().amount()).isEqualByComparingTo("-1100000.00");

        assertThatThrownBy(() -> inTransaction(() -> {
            StudentCurrentAccount account = service.lockStudentCurrentAccount(accountId);
            return service.recordPaymentReversalDebit(account, entryRepository.findById(paymentEntryId).orElseThrow(), DAY, "Otra vez");
        })).isInstanceOf(CurrentAccountEntrySourceMismatchException.class);
        assertThat(jdbc.queryForObject("select count(*) from current_account_entry where compensates_entry_id = ?", Integer.class, paymentEntryId)).isEqualTo(1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void recordingOnAnUnlockedAccountFailsAndNothingIsPersisted() {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);

        assertThatThrownBy(() -> inTransaction(() -> {
            StudentCurrentAccount unlocked = accountRepository.findById(accountId).orElseThrow();
            return service.recordPaymentReceivedCredit(unlocked, BillingTestFixtures.seedApprovedPaymentRow(unlocked.getId(), ars("10.00")), ars("10.00"), DAY, "Pago");
        })).isInstanceOf(IllegalStateException.class);
        StudentCurrentAccount detached = loadAccount(accountId);
        assertThatThrownBy(() -> service.recordPaymentReceivedCredit(detached, BillingTestFixtures.seedApprovedPaymentRow(detached.getId(), ars("10.00")), ars("10.00"), DAY, "Pago")).isInstanceOf(IllegalTransactionStateException.class);

        assertThat(entryRepository.findByStudentCurrentAccountIdOrderBySequenceNumberAsc(accountId)).isEmpty();
    }

    @Test
    @Tag("D-144")
    void recordingOnAnAccountWrittenButNotLockedInTheTransactionFails() {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);

        assertThatThrownBy(() -> inTransaction(() -> {
            StudentCurrentAccount unlocked = accountRepository.findById(accountId).orElseThrow();
            entityManager.lock(unlocked, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
            return service.recordPaymentReceivedCredit(unlocked, BillingTestFixtures.seedApprovedPaymentRow(unlocked.getId(), ars("10.00")), ars("10.00"), DAY, "Pago");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("must be locked");
        assertThat(entryRepository.findByStudentCurrentAccountIdOrderBySequenceNumberAsc(accountId)).isEmpty();
    }

    @Test
    void anAccountLockedInAnEarlierTransactionIsNotConsideredLockedInALaterOne() {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        inTransaction(() -> service.lockStudentCurrentAccount(accountId));

        assertThatThrownBy(() -> inTransaction(() -> {
            StudentCurrentAccount unlocked = accountRepository.findById(accountId).orElseThrow();
            return service.recordPaymentReceivedCredit(unlocked, BillingTestFixtures.seedApprovedPaymentRow(unlocked.getId(), ars("10.00")), ars("10.00"), DAY, "Pago");
        })).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Tag("D-144")
    @Timeout(60)
    void anAccountLockedByTheOuterTransactionIsNotConsideredLockedInANestedRequiresNewTransactionAndRemainsLockedAfterwards() {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        TransactionTemplate nestedTransaction = new TransactionTemplate(transactionTemplate.getTransactionManager());
        nestedTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        inTransaction(() -> {
            StudentCurrentAccount lockedByOuter = service.lockStudentCurrentAccount(accountId);

            assertThatThrownBy(() -> nestedTransaction.execute(status -> {
                StudentCurrentAccount unlockedInNested = accountRepository.findById(accountId).orElseThrow();
                return service.recordPaymentReceivedCredit(unlockedInNested, BillingTestFixtures.seedApprovedPaymentRow(unlockedInNested.getId(), ars("10.00")), ars("10.00"), DAY, "Pago anidado");
            })).isInstanceOf(IllegalStateException.class).hasMessageContaining("must be locked");

            return service.recordPaymentReceivedCredit(lockedByOuter, BillingTestFixtures.seedApprovedPaymentRow(lockedByOuter.getId(), ars("10.00")), ars("10.00"), DAY, "Pago externo");
        });

        assertThat(entryRepository.findByStudentCurrentAccountIdOrderBySequenceNumberAsc(accountId))
                .extracting(CurrentAccountEntry::getConcept).containsExactly("Pago externo");
    }

    @Test
    void theEntriesCannotBeUpdatedNorDeleted() {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        Long entryId = inTransaction(() -> service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), BillingTestFixtures.seedApprovedPaymentRow(accountId, ars("10.00")), ars("10.00"), DAY, "Pago").getId());

        assertThatThrownBy(() -> jdbc.update("update current_account_entry set concept = 'otro' where id = ?", entryId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from current_account_entry where id = ?", entryId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Tag("D-144")
    void theEffectiveDateIsTheBusinessDayOfBuenosAiresEvenWithTheJvmInUtc() {
        ApplicationTime.useClock(MutableClock.INSTANCE);
        Long accountId = newAccountId(newStudentId(), Currency.ARS);

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 11, 0, 30));
        inTransaction(() -> service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), BillingTestFixtures.seedApprovedPaymentRow(accountId, ars("10.00")), ars("10.00"), ApplicationTime.today(), "Pago"));
        MutableClock.INSTANCE.setInstant(java.time.Instant.parse("2027-03-11T02:30:00Z"));
        inTransaction(() -> service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), BillingTestFixtures.seedApprovedPaymentRow(accountId, ars("10.00")), ars("10.00"), ApplicationTime.today(), "Pago"));

        List<CurrentAccountEntry> entries = entryRepository.findByStudentCurrentAccountIdOrderBySequenceNumberAsc(accountId);
        assertThat(entries.get(0).getEffectiveDate()).isEqualTo(LocalDate.of(2027, 3, 11));
        assertThat(entries.get(1).getEffectiveDate()).isEqualTo(LocalDate.of(2027, 3, 10));
    }

    @Test
    void aTwelveMonthSequenceKeepsEveryInvariantAtEveryStep() {
        for (long seed : new long[]{11L, 222L, 3333L}) {
            ApplicationTime.useClock(MutableClock.INSTANCE);
            MutableClock.INSTANCE.setDate(LocalDate.of(2027, 1, 1));
            Random random = new Random(seed);
            Long studentId = newStudentId();
            Long accountId = newAccountId(studentId, Currency.ARS);
            List<Long> paymentEntries = new java.util.ArrayList<>();
            java.util.Set<Long> reversed = new java.util.HashSet<>();
            for (int step = 0; step < 365; step += 6) {
                MutableClock.INSTANCE.advance(java.time.Duration.ofDays(6));
                LocalDate today = ApplicationTime.today();
                String amount = (random.nextInt(90000) + 1) + "." + String.format("%02d", random.nextInt(100));
                int kind = random.nextInt(5);
                long id = BillingTestFixtures.seedApprovedPaymentRow(accountId);
                switch (kind) {
                    case 0 -> {
                        Long invoiceId = newIssuedInvoiceId(accountId, Currency.ARS, amount);
                        inTransaction(() -> service.recordInvoiceIssuedDebit(service.lockStudentCurrentAccount(accountId), invoiceRepository.findById(invoiceId).orElseThrow(), today));
                    }
                    case 1 -> paymentEntries.add(inTransaction(() -> service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), id, ars(amount), today, "Pago").getId()));
                    case 2 -> {
                        Long creditedInvoiceId = newIssuedInvoiceId(accountId, Currency.ARS, "100.00");
                        long creditNoteId = newCreditNoteId(accountId, creditedInvoiceId, "0.00", amount);
                        inTransaction(() -> {
                            StudentCurrentAccount locked = service.lockStudentCurrentAccount(accountId);
                            service.recordInvoiceIssuedDebit(locked, invoiceRepository.findById(creditedInvoiceId).orElseThrow(), today);
                            return service.recordCreditNoteIssuedCredit(locked, creditNoteId, ars("0.00"), ars(amount), today, "Nota de crédito");
                        });
                    }
                    case 3 -> {
                        if (!paymentEntries.isEmpty()) {
                            Long target = paymentEntries.get(random.nextInt(paymentEntries.size()));
                            BigDecimal unallocated = loadAccount(accountId).getUnallocatedCreditAmount().value().amount();
                            BigDecimal targetAmount = entryRepository.findById(target).orElseThrow().getAmount().value().amount();
                            if (!reversed.contains(target) && targetAmount.compareTo(unallocated) <= 0) {
                                inTransaction(() -> service.recordPaymentReversalDebit(service.lockStudentCurrentAccount(accountId), entryRepository.findById(target).orElseThrow(), today, "Contracargo"));
                                reversed.add(target);
                            }
                        }
                    }
                    default -> {
                        BigDecimal unallocated = loadAccount(accountId).getUnallocatedCreditAmount().value().amount();
                        if (unallocated.signum() > 0) {
                            inTransaction(() -> service.recordRefundPaidDebit(service.lockStudentCurrentAccount(accountId), id, ars(unallocated.toPlainString()), today, "Devolución"));
                        }
                    }
                }
                invariants.assertAllBillingInvariantsHold();
            }
            assertThat(entryRepository.findByStudentCurrentAccountIdOrderBySequenceNumberAsc(accountId)).isNotEmpty();
        }
    }

    @Test
    void anEntryTypeIsPersistedWithItsDirection() {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        CurrentAccountEntry entry = inTransaction(() -> service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), BillingTestFixtures.seedApprovedPaymentRow(accountId, ars("10.00")), ars("10.00"), DAY, "Pago"));

        CurrentAccountEntry loaded = entryRepository.findById(entry.getId()).orElseThrow();

        assertThat(loaded.getEntryType()).isEqualTo(CurrentAccountEntryType.PAYMENT_RECEIVED);
        assertThat(loaded.getRunningBalance().side()).isEqualTo(CurrentAccountBalanceSide.IN_FAVOR);
        assertThat(loaded.getRunningBalance().amount()).isEqualTo(ars("10.00"));
        assertThat(loaded.getAmount().value().currency()).isEqualTo(Currency.ARS);
    }
}
