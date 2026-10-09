package com.red_escuela.backend.billing.invoice.repository;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.repository.StudentCurrentAccountRepository;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.*;
import com.red_escuela.backend.billing.invoice.service.InvoiceNumberGenerator;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-171")
@Tag("D-097")
@Tag("D-009")
@Tag("D-021")
@Tag("D-051")
class InvoicePersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private StudentCurrentAccountRepository accountRepository;
    @Autowired
    private InvoiceNumberGenerator numberGenerator;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private InstitutionRepository institutionRepository;
    @Autowired
    private StudentRepository studentRepository;

    private BillingInvariantChecker invariants;

    @org.junit.jupiter.api.BeforeEach
    void startBillingInvariantBaseline() {
        invariants = BillingInvariantChecker.startingNow(jdbc);
    }

    private StudentCurrentAccount newAccount() {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        String username = "v2" + System.nanoTime();
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
        student = studentRepository.save(student);
        return accountRepository.save(StudentCurrentAccount.openStudentCurrentAccount(student.getId(), Currency.ARS));
    }

    private Invoice draftFor(StudentCurrentAccount account, String chargeKey) {
        InvoiceDraftHeader header = new InvoiceDraftHeader(account.getId(), Currency.ARS, InvoiceDocumentType.INVOICE,
                InvoiceType.ADMINISTRATIVE_FEE, InvoiceIssuingReason.AD_HOC_CHARGE, null);
        InvoiceLineDraft line = new InvoiceLineDraft(FeeType.ADMINISTRATIVE_FEE, "Tasa", java.time.YearMonth.of(2027, 3),
                null, null, null, null, null, chargeKey, ars("80000.00"));
        Invoice invoice = InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, ACTOR);
        invoice.addDiscountToDraftLine(1, discount("80000.00", "10000.50"));
        return invoice;
    }

    @Test
    void anIssuedInvoiceWithLineDiscountHistoryAndSnapshotRoundTrips() {
        StudentCurrentAccount account = newAccount();
        Invoice draft = draftFor(account, "PE:rt" + System.nanoTime());
        draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), ISSUE_DATE, DUE_DATE, SNAPSHOT, ACTOR);
        Long id = invoiceRepository.save(draft).getId();

        Invoice loaded = transactionTemplate.execute(status -> {
            Invoice invoice = invoiceRepository.findInvoiceWithLinesById(id).orElseThrow();
            assertThat(invoice.getStatusChanges()).hasSize(3);
            assertThat(invoice.getLines().get(0).getDiscount().getDiscountAmount()).isEqualTo(ars("10000.50"));
            invoice.getStatusChanges().size();
            return invoice;
        });

        assertThat(loaded.getDocumentStatus()).isEqualTo(InvoiceDocumentStatus.ISSUED);
        assertThat(loaded.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(loaded.getStudentSnapshot()).isEqualTo(SNAPSHOT);
        assertThat(loaded.getAmounts().total()).isEqualTo(ars("69999.50"));
        assertThat(loaded.getAmounts().total().value().currency()).isEqualTo(Currency.ARS);
        assertThat(loaded.getInvoiceNumber().value()).startsWith("F-");
        assertThat(loaded.getVersion()).isNotNull();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(accountRepository.findByStudentIdAndCurrency(account.getStudentId(), Currency.ARS)).isPresent();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void paymentAndDiscardTransitionsArePersistedWithTheirHistory() {
        StudentCurrentAccount account = newAccount();
        Invoice invoice = draftFor(account, "PE:tr" + System.nanoTime());
        invoice.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), ISSUE_DATE, DUE_DATE, SNAPSHOT, ACTOR);
        Long id = invoiceRepository.save(invoice).getId();

        transactionTemplate.executeWithoutResult(status -> {
            Invoice managed = invoiceRepository.findInvoiceWithLinesById(id).orElseThrow();
            managed.allocateFullInvoicePayment(managed.getAmounts().outstandingAmount(), DUE_DATE, ACTOR);
        });
        transactionTemplate.executeWithoutResult(status -> {
            Invoice managed = invoiceRepository.findById(id).orElseThrow();
            managed.reversePaymentAllocation(DUE_DATE.plusDays(5), 0, ACTOR, "contracargo");
        });

        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, id)).isEqualTo("OVERDUE");
        assertThat(jdbc.queryForObject("select count(*) from invoice_status_change where invoice_id = ? and status_kind = 'PAYMENT'", Integer.class, id)).isEqualTo(3);
        invariants.assertAllBillingInvariantsHold();

        Invoice draft = invoiceRepository.save(draftFor(account, "PE:ds" + System.nanoTime()));
        transactionTemplate.executeWithoutResult(status -> invoiceRepository.findInvoiceWithLinesById(draft.getId()).orElseThrow().discardDraftInvoice("error", ACTOR));
        assertThat(jdbc.queryForObject("select count(*) from invoice_line where invoice_id = ? and charge_key_released_at is not null", Integer.class, draft.getId())).isEqualTo(1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theSequencesNeverRepeatANumberUnderConcurrency() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<InvoiceNumber>> futures = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            InvoiceDocumentType type = i % 2 == 0 ? InvoiceDocumentType.INVOICE : InvoiceDocumentType.DEBIT_NOTE;
            futures.add(pool.submit(() -> {
                start.await();
                return numberGenerator.nextInvoiceNumber(type);
            }));
        }
        start.countDown();
        Set<String> numbers = new HashSet<>();
        for (Future<InvoiceNumber> future : futures) {
            numbers.add(future.get().value());
        }
        pool.shutdown();

        assertThat(numbers).hasSize(16);
        assertThat(numbers).filteredOn(n -> n.startsWith("ND-")).hasSize(8);
    }

    @Test
    void theStatusHistoryIsAppendOnly() {
        StudentCurrentAccount account = newAccount();
        Long id = invoiceRepository.save(draftFor(account, "PE:ao" + System.nanoTime())).getId();

        assertThatThrownBy(() -> jdbc.update("update invoice_status_change set reason = 'x' where invoice_id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from invoice_status_change where invoice_id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("append-only");
    }

    @Test
    void twoConcurrentDraftsWithTheSameChargeKeyLeaveOneAndTheOtherGetsAConflictNever500() throws Exception {
        StudentCurrentAccount account = newAccount();
        String key = "PE:conc" + System.nanoTime() + ":MONTHLY_INSTALLMENT:2027-03";
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                Invoice draft = draftFor(account, key);
                ready.countDown();
                go.await();
                try {
                    return invoiceRepository.save(draft).getId();
                } catch (DataIntegrityViolationException conflict) {
                    return conflict;
                }
            }));
        }
        ready.await();
        go.countDown();
        int saved = 0;
        int conflicts = 0;
        for (Future<Object> future : futures) {
            Object result = future.get();
            if (result instanceof DataIntegrityViolationException) {
                conflicts++;
            } else {
                saved++;
            }
        }
        pool.shutdown();

        assertThat(saved).isEqualTo(1);
        assertThat(conflicts).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from invoice_line where charge_key = ?", Integer.class, key)).isEqualTo(1);
        Long winner = jdbc.queryForObject("select invoice_id from invoice_line where charge_key = ?", Long.class, key);
        transactionTemplate.executeWithoutResult(status -> invoiceRepository.findInvoiceWithLinesById(winner).orElseThrow().discardDraftInvoice("repetir", ACTOR));
        Invoice again = invoiceRepository.save(draftFor(account, key));
        assertThat(again.getId()).isNotNull();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void anIssuedInvoiceNeverReleasesItsChargeKey() {
        StudentCurrentAccount account = newAccount();
        String key = "PE:iss" + System.nanoTime() + ":MONTHLY_INSTALLMENT:2027-03";
        Invoice issued = draftFor(account, key);
        issued.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), ISSUE_DATE, DUE_DATE, SNAPSHOT, ACTOR);
        invoiceRepository.save(issued);

        assertThatThrownBy(() -> invoiceRepository.save(draftFor(account, key))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theLegacyInvoiceTablesNoLongerExistAndIncomingForeignKeysPointToTheNewInvoice() {
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name in ('legacy_invoice', 'invoice_adjustment')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint where contype = 'f' and confrelid = 'invoice'::regclass "
                + "and conrelid = 'legacy_payment_allocation'::regclass", Integer.class)).isGreaterThan(0);
        assertThat(Map.of("v", LocalDate.of(2027, 1, 1))).isNotEmpty();
    }

    @Test
    void eventsRecordedBeforeTheFirstSaveCarryTheAssignedIdWhenPulled() {
        StudentCurrentAccount account = newAccount();
        Invoice invoice = draftFor(account, "PE:ev" + System.nanoTime());
        invoice.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), ISSUE_DATE, DUE_DATE, SNAPSHOT, ACTOR);

        Invoice saved = invoiceRepository.save(invoice);

        assertThat(saved.pullRecordedDomainEvents()).singleElement().satisfies(event ->
                assertThat(((com.red_escuela.backend.billing.invoice.domain.event.InvoiceIssuedEvent) event).invoiceId()).isEqualTo(saved.getId()));
    }

    @Test
    void freeCreditThenReversalThenFullCancellationWorksAndPersists() {
        StudentCurrentAccount account = newAccount();
        Invoice invoice = draftFor(account, "PE:cr" + System.nanoTime());
        invoice.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), ISSUE_DATE, DUE_DATE, SNAPSHOT, ACTOR);
        Long id = invoiceRepository.save(invoice).getId();
        var total = ars("69999.50");
        var freeCredit = new InvoiceCreditNoteApplication(CreditNoteApplicationKind.DEBT_REDUCTION, ars("0.00"), ars("10000.00"), List.of(new InvoiceLineCredit(1, ars("10000.00"))));
        var cancellation = new InvoiceCreditNoteApplication(CreditNoteApplicationKind.CANCELLATION, total, ars("0.00"), List.of(new InvoiceLineCredit(1, total)));

        transactionTemplate.executeWithoutResult(s -> invoiceRepository.findInvoiceWithLinesById(id).orElseThrow().allocateFullInvoicePayment(total, DUE_DATE, ACTOR));
        transactionTemplate.executeWithoutResult(s -> invoiceRepository.findInvoiceWithLinesById(id).orElseThrow().applyCreditNote(freeCredit, DUE_DATE, ACTOR));
        transactionTemplate.executeWithoutResult(s -> invoiceRepository.findInvoiceWithLinesById(id).orElseThrow().reversePaymentAllocation(DUE_DATE, 0, ACTOR, "contracargo"));
        transactionTemplate.executeWithoutResult(s -> invoiceRepository.findInvoiceWithLinesById(id).orElseThrow().applyCreditNote(cancellation, DUE_DATE, ACTOR));

        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select credited_amount from invoice_line where invoice_id = ?", java.math.BigDecimal.class, id)).isEqualByComparingTo("69999.50");
        assertThat(jdbc.queryForObject("select free_credited_amount from invoice_line where invoice_id = ?", java.math.BigDecimal.class, id)).isEqualByComparingTo("10000.00");
        invariants.assertAllBillingInvariantsHold();
    }
}
