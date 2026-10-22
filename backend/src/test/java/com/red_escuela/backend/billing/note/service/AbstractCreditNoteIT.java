package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.TestTemplateTerms;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.shared.domain.StatusType;
import java.util.Optional;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.AbstractCurrentAccountIT;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.shared.domain.Currency;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;

public abstract class AbstractCreditNoteIT extends AbstractCurrentAccountIT {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected RoleRepository roleRepository;
    @Autowired
    protected JwtService jwtService;
    @Autowired
    protected EntityManager entityManager;
    @Autowired
    protected StudentRepository studentRepository;
    @Autowired
    protected CurrentAccountAllocationService allocationService;

    protected record Staff(Long personId, String token) {
    }

    protected Staff newStaff(String role) {
        Person staff = newStaffPerson();
        String token = inTransaction(() -> {
            Person managed = entityManager.find(Person.class, staff.getId());
            managed.getRoles().add(roleRepository.findByName(role).orElseThrow());
            return jwtService.generateAccessToken(managed);
        });
        return new Staff(staff.getId(), token);
    }

    protected Long issueInvoiceOfStudent(Long studentId, Long accountId, String amount, LocalDate dueDate) {
        return inTransaction(() -> {
            Person person = studentRepository.findById(studentId).orElseThrow().getPerson();
            StudentSnapshot snapshot = new StudentSnapshot(person.getName() + " " + person.getLastname(), person.getDni(), "Calle 1");
            InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.ADMINISTRATIVE_FEE,
                    InvoiceIssuingReason.AD_HOC_CHARGE, null);
            InvoiceLineDraft line = new InvoiceLineDraft(FeeType.ADMINISTRATIVE_FEE, "Concepto", YearMonth.of(2027, 3), null, null, null, null, null,
                    "NC:" + System.nanoTime(), ars(amount));
            Invoice draft = InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, ACTOR);
            draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), dueDate.minusDays(9), dueDate, snapshot, ACTOR);
            Invoice saved = invoiceRepository.save(draft);
            service.recordInvoiceIssuedDebit(service.lockStudentCurrentAccount(accountId), saved, dueDate.minusDays(9));
            return saved.getId();
        });
    }

    protected void payAccount(Long accountId, String amount, LocalDate paidOn) {
        inTransaction(() -> {
            StudentCurrentAccount account = service.lockStudentCurrentAccount(accountId);
            service.recordPaymentReceivedCredit(account, BillingTestFixtures.seedApprovedPaymentRow(account.getId(), ars(amount)), ars(amount), paidOn, "Pago");
            allocationService.allocateAccountCreditToOldestInvoices(account, paidOn, ACTOR);
            return null;
        });
    }

    protected String creditNoteBody(String reason, String justification, int lineNumber, String amount) {
        return "{\"reason\":\"" + reason + "\",\"justification\":" + (justification == null ? "null" : "\"" + justification + "\"")
                + ",\"lines\":[{\"lineNumber\":" + lineNumber + ",\"amount\":{\"amount\":" + new BigDecimal(amount).toPlainString() + ",\"currency\":\"ARS\"}}]}";
    }

    protected String outstandingOf(Long invoiceId) {
        return amount("select outstanding_amount from invoice where id = ?", invoiceId).toPlainString();
    }

    protected long creditNoteCount() {
        return jdbc.queryForObject("select count(*) from credit_note", Long.class);
    }

    protected long ledgerEntryCount(Long accountId) {
        return jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ?", Long.class, accountId);
    }

    protected Long templateStartingOn(Program program, LocalDate start) {
        Long creatorId = newStaffPerson().getId();
        return inTransaction(() -> {
            Term term = new Term();
            term.setYear(start.getYear());
            term.setStatus(StatusType.ACTIVE);
            term.setStartDate(start.atStartOfDay());
            term.setEndDate(LocalDate.of(start.getYear(), 12, 31).atStartOfDay());
            entityManager.persist(term);
            ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
            template.setProgram(entityManager.find(Program.class, program.getId()));
            template.setTerm(term);
            TestTemplateTerms.setRecurringFeeInvoicesCount(template, 10);
            template.setCreatedBy(entityManager.find(Person.class, creatorId));
            entityManager.persist(template);
            return template.getId();
        });
    }

    protected Long enrollmentFeeOf(ProgramEnrollment enrollment, Long templateId, int year) {
        Long accountId = newAccountId(enrollment.getStudent().getId(), Currency.ARS);
        Long priceVersionId = PriceFixtures.persistPriceVersion(enrollment.getProgram().getId(), null, FeeType.ENROLLMENT_FEE, "Matrícula",
                ars("50000.00").value(), LocalDate.of(2000, 1, 1).plusDays(year), enrollment.getStudent().getPerson().getId());
        return inTransaction(() -> {
            Person person = studentRepository.findById(enrollment.getStudent().getId()).orElseThrow().getPerson();
            StudentSnapshot snapshot = new StudentSnapshot(person.getName() + " " + person.getLastname(), person.getDni(), "Calle 1");
            InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.ENROLLMENT_FEE,
                    InvoiceIssuingReason.MONTHLY_RUN, null);
            InvoiceLineDraft line = new InvoiceLineDraft(FeeType.ENROLLMENT_FEE, "Matrícula " + year, YearMonth.of(year, 2), enrollment.getId(), null, null, null,
                    priceVersionId, "PE:" + enrollment.getId() + ":ENROLLMENT_FEE:" + year, ars("50000.00"), templateId, 1);
            Invoice draft = InvoiceFactory.createEnrollmentFeeInvoiceDraft(header, line, Optional.empty(), ACTOR);
            draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), LocalDate.of(2027, 11, 1), LocalDate.of(2027, 12, 1), snapshot, ACTOR);
            Invoice saved = invoiceRepository.save(draft);
            service.recordInvoiceIssuedDebit(service.lockStudentCurrentAccount(accountId), saved, LocalDate.of(2027, 11, 1));
            return saved.getId();
        });
    }
}
