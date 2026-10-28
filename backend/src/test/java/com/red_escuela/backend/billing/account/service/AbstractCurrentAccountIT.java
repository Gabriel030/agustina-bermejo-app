package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.repository.CurrentAccountEntryRepository;
import com.red_escuela.backend.billing.account.repository.StudentCurrentAccountRepository;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.InvoiceNumberGenerator;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Supplier;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.DUE_DATE;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ISSUE_DATE;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.SNAPSHOT;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-144")
public abstract class AbstractCurrentAccountIT extends AbstractIntegrationTest {

    @Autowired
    protected StudentCurrentAccountService service;
    @Autowired
    protected StudentCurrentAccountRepository accountRepository;
    @Autowired
    protected CurrentAccountEntryRepository entryRepository;
    @Autowired
    protected InvoiceRepository invoiceRepository;
    @Autowired
    protected InvoiceNumberGenerator numberGenerator;
    @Autowired
    protected TransactionTemplate transactionTemplate;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private InstitutionRepository institutionRepository;
    @Autowired
    private StudentRepository studentRepository;

    private static final java.util.concurrent.atomic.AtomicLong EXTERNAL_IDS = new java.util.concurrent.atomic.AtomicLong(System.nanoTime() % 1_000_000_000L * 1000);

    protected static long nextExternalId() {
        return EXTERNAL_IDS.incrementAndGet();
    }

    protected long newCreditNoteId(Long accountId, Long invoiceId, String offset, String free) {
        java.math.BigDecimal offsetAmount = new java.math.BigDecimal(offset);
        java.math.BigDecimal freeAmount = new java.math.BigDecimal(free);
        String number = "NC-" + String.format("%08d", nextExternalId() % 100_000_000L);
        return jdbc.queryForObject(
                "insert into credit_note (credit_note_number, student_current_account_id, currency, invoice_id, invoice_number, reason, justification, "
                        + "offset_amount, offset_amount_currency, free_credit_amount, free_credit_amount_currency, total_amount, total_amount_currency, "
                        + "issue_date, fact_date, recipient_snapshot, issued_by_process, recorded_at) "
                        + "select ?, ?, i.currency, i.id, i.invoice_number, 'BILLING_ERROR', 'Nota de prueba', ?, i.currency, ?, i.currency, ?, i.currency, "
                        + "current_date, current_date, cast('{}' as jsonb), 'TEST', now() from invoice i where i.id = ? returning id",
                Long.class, number, accountId, offsetAmount, freeAmount, offsetAmount.add(freeAmount), invoiceId);
    }

    protected <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }

    protected Long newStudentId() {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        String username = "acc" + System.nanoTime();
        person.setUsername(username);
        person.setDni(Long.toString(Math.abs((long) (username).hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person = personRepository.save(person);
        Institution institution = new Institution();
        institution.setName("Instituto " + System.nanoTime());
        institution = institutionRepository.save(institution);
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student).getId();
    }

    protected Long newAccountId(Long studentId, Currency currency) {
        return inTransaction(() -> service.findOrOpenAndLockStudentCurrentAccount(studentId, currency).getId());
    }

    protected Long newIssuedInvoiceId(Long accountId, Currency currency, String amount) {
        InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, currency, InvoiceDocumentType.INVOICE, InvoiceType.ADMINISTRATIVE_FEE,
                InvoiceIssuingReason.AD_HOC_CHARGE, null);
        InvoiceLineDraft line = new InvoiceLineDraft(FeeType.ADMINISTRATIVE_FEE, "Tasa", YearMonth.of(2027, 3), null, null, null, null, null,
                "PE:acc" + System.nanoTime(), NonNegativeMoney.of(new BigDecimal(amount), currency));
        Invoice invoice = InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, ACTOR);
        invoice.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), ISSUE_DATE, DUE_DATE, SNAPSHOT, ACTOR);
        return invoiceRepository.save(invoice).getId();
    }

    protected Long issueAdministrativeFeeWithLedgerDebit(Long accountId, String amount, LocalDate dueDate) {
        return inTransaction(() -> {
            InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.ADMINISTRATIVE_FEE,
                    InvoiceIssuingReason.AD_HOC_CHARGE, null);
            InvoiceLineDraft line = new InvoiceLineDraft(FeeType.ADMINISTRATIVE_FEE, "Concepto", YearMonth.of(2027, 3), null, null, null, null, null,
                    "AB:" + System.nanoTime(), ars(amount));
            Invoice draft = InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, ACTOR);
            draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), dueDate.minusDays(9), dueDate, SNAPSHOT, ACTOR);
            Invoice saved = invoiceRepository.save(draft);
            service.recordInvoiceIssuedDebit(service.lockStudentCurrentAccount(accountId), saved, dueDate.minusDays(9));
            return saved.getId();
        });
    }

    protected Person newStaffPerson() {
        return inTransaction(() -> studentRepository.findById(newStudentId()).orElseThrow().getPerson());
    }

    protected StudentCurrentAccount loadAccount(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow();
    }

    protected BigDecimal amount(String sql, Object... args) {
        return jdbc.queryForObject(sql, BigDecimal.class, args);
    }
}
