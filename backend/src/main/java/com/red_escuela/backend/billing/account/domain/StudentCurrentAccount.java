package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.billing.account.exception.CurrentAccountEntrySourceMismatchException;
import com.red_escuela.backend.billing.account.exception.InsufficientAccountCreditException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.exception.StudentCurrentAccountCurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;

@Entity
@Table(name = "student_current_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentCurrentAccount {

    private static final int MAX_CONCEPT_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, updatable = false)
    private Currency currency;

    @Embedded
    private CurrentAccountBalance balance;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "unallocated_credit_amount", nullable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "unallocated_credit_amount_currency", nullable = false))})
    private NonNegativeMoney unallocatedCreditAmount;

    @Column(name = "last_entry_sequence", nullable = false)
    private long lastEntrySequence;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private LocalDateTime openedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private final Set<Long> reversedCreditEntryIds = new HashSet<>();

    private StudentCurrentAccount(Long studentId, Currency currency) {
        this.studentId = studentId;
        this.currency = currency;
        this.balance = CurrentAccountBalance.zero(currency);
        this.unallocatedCreditAmount = NonNegativeMoney.zero(currency);
        this.lastEntrySequence = 0;
        this.openedAt = ApplicationTime.now();
    }

    public static StudentCurrentAccount openStudentCurrentAccount(Long studentId, Currency currency) {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(currency, "currency");
        return new StudentCurrentAccount(studentId, currency);
    }

    public Optional<CurrentAccountEntry> recordInvoiceIssuedDebit(Invoice issuedInvoice, LocalDate effectiveDate) {
        return recordIssuedDocumentDebit(issuedInvoice, InvoiceDocumentType.INVOICE, CurrentAccountEntryType.INVOICE_ISSUED, "Factura ", effectiveDate);
    }

    public Optional<CurrentAccountEntry> recordDebitNoteIssuedDebit(Invoice issuedDebitNote, LocalDate effectiveDate) {
        return recordIssuedDocumentDebit(issuedDebitNote, InvoiceDocumentType.DEBIT_NOTE, CurrentAccountEntryType.DEBIT_NOTE_ISSUED, "Nota de débito ", effectiveDate);
    }

    public CurrentAccountEntry recordPaymentReceivedCredit(Long paymentId, NonNegativeMoney amount, LocalDate paidOn, String concept) {
        Objects.requireNonNull(paymentId, "paymentId");
        requirePositiveAmountInAccountCurrency(amount);
        CurrentAccountEntry entry = appendEntry(CurrentAccountEntryType.PAYMENT_RECEIVED, amount, paidOn, concept, CurrentAccountEntrySource.ofPayment(paymentId));
        unallocatedCreditAmount = unallocatedCreditAmount.plus(amount);
        return entry;
    }

    public void allocateUnallocatedCreditToInvoicePayment(NonNegativeMoney amount) {
        requireAccountCurrency(amount);
        requireUnallocatedCreditCovers(amount);
        unallocatedCreditAmount = unallocatedCreditAmount.minusNotBelowZero(amount);
    }

    public CurrentAccountEntry recordCreditNoteIssuedCredit(Long creditNoteId, NonNegativeMoney offsetAmount, NonNegativeMoney freeCreditAmount,
                                                           LocalDate effectiveDate, String concept) {
        Objects.requireNonNull(creditNoteId, "creditNoteId");
        requireAccountCurrency(offsetAmount);
        requireAccountCurrency(freeCreditAmount);
        NonNegativeMoney total = offsetAmount.plus(freeCreditAmount);
        requirePositiveAmountInAccountCurrency(total);
        CurrentAccountEntry entry = appendEntry(CurrentAccountEntryType.CREDIT_NOTE_ISSUED, total, effectiveDate, concept, CurrentAccountEntrySource.ofCreditNote(creditNoteId));
        unallocatedCreditAmount = unallocatedCreditAmount.plus(freeCreditAmount);
        return entry;
    }

    public CurrentAccountEntry recordPaymentReversalDebit(CurrentAccountEntry originalPaymentCredit, LocalDate effectiveDate, String reason) {
        Objects.requireNonNull(originalPaymentCredit, "originalPaymentCredit");
        boolean isThisAccountsPaymentCredit = originalPaymentCredit.getEntryType() == CurrentAccountEntryType.PAYMENT_RECEIVED
                && Objects.equals(originalPaymentCredit.getStudentCurrentAccountId(), id)
                && originalPaymentCredit.getId() != null
                && !reversedCreditEntryIds.contains(originalPaymentCredit.getId());
        if (!isThisAccountsPaymentCredit) {
            throw new CurrentAccountEntrySourceMismatchException();
        }
        NonNegativeMoney amount = originalPaymentCredit.getAmount();
        requireUnallocatedCreditCovers(amount);
        CurrentAccountEntry entry = appendEntry(CurrentAccountEntryType.PAYMENT_REVERSED, amount, effectiveDate, reason,
                CurrentAccountEntrySource.ofPaymentReversal(originalPaymentCredit.getPaymentId(), originalPaymentCredit.getId()));
        unallocatedCreditAmount = unallocatedCreditAmount.minusNotBelowZero(amount);
        reversedCreditEntryIds.add(originalPaymentCredit.getId());
        return entry;
    }

    public void rememberReversedCreditEntry(Long creditEntryId) {
        reversedCreditEntryIds.add(Objects.requireNonNull(creditEntryId, "creditEntryId"));
    }

    public CurrentAccountEntry recordRefundPaidDebit(Long refundId, NonNegativeMoney amount, LocalDate effectiveDate, String concept) {
        Objects.requireNonNull(refundId, "refundId");
        requirePositiveAmountInAccountCurrency(amount);
        requireUnallocatedCreditCovers(amount);
        CurrentAccountEntry entry = appendEntry(CurrentAccountEntryType.REFUND_PAID, amount, effectiveDate, concept, CurrentAccountEntrySource.ofRefund(refundId));
        unallocatedCreditAmount = unallocatedCreditAmount.minusNotBelowZero(amount);
        return entry;
    }

    public Money calculateSignedBalance() {
        return balance.toSignedAmount();
    }

    private Optional<CurrentAccountEntry> recordIssuedDocumentDebit(Invoice document, InvoiceDocumentType expectedType, CurrentAccountEntryType entryType,
                                                                   String conceptPrefix, LocalDate effectiveDate) {
        Objects.requireNonNull(document, "document");
        boolean belongsHere = document.getDocumentStatus() == InvoiceDocumentStatus.ISSUED
                && document.getDocumentType() == expectedType
                && document.getId() != null
                && Objects.equals(document.getStudentCurrentAccountId(), id);
        if (!belongsHere) {
            throw new CurrentAccountEntrySourceMismatchException();
        }
        NonNegativeMoney total = document.getAmounts().total();
        requireAccountCurrency(total);
        if (document.getCurrency() != currency) {
            throw new StudentCurrentAccountCurrencyMismatchException();
        }
        if (total.value().isZero()) {
            return Optional.empty();
        }
        return Optional.of(appendEntry(entryType, total, effectiveDate, conceptPrefix + document.getInvoiceNumber().value(),
                CurrentAccountEntrySource.ofInvoice(document.getId())));
    }

    private CurrentAccountEntry appendEntry(CurrentAccountEntryType entryType, NonNegativeMoney amount, LocalDate effectiveDate, String concept,
                                           CurrentAccountEntrySource source) {
        Objects.requireNonNull(effectiveDate, "effectiveDate");
        if (concept == null || concept.isBlank() || concept.length() > MAX_CONCEPT_LENGTH) {
            throw new IllegalArgumentException("The concept must have between 1 and " + MAX_CONCEPT_LENGTH + " characters");
        }
        CurrentAccountBalance nextBalance = balance.applyEntry(entryType.direction(), amount);
        lastEntrySequence = lastEntrySequence + 1;
        balance = nextBalance;
        return new CurrentAccountEntry(id, currency, lastEntrySequence, entryType, amount, nextBalance, effectiveDate, concept, source);
    }

    private void requireAccountCurrency(NonNegativeMoney money) {
        Objects.requireNonNull(money, "money");
        if (money.value().currency() != currency) {
            throw new StudentCurrentAccountCurrencyMismatchException();
        }
    }

    private void requirePositiveAmountInAccountCurrency(NonNegativeMoney money) {
        requireAccountCurrency(money);
        if (money.value().isZero()) {
            throw new IllegalArgumentException("An account entry amount must be greater than zero");
        }
    }

    private void requireUnallocatedCreditCovers(NonNegativeMoney amount) {
        if (amount.value().isGreaterThan(unallocatedCreditAmount.value())) {
            throw new InsufficientAccountCreditException();
        }
    }
}
