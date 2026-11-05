package com.red_escuela.backend.testsupport.system;

import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
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
import com.red_escuela.backend.identity.service.LocalSeedRunner;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.SNAPSHOT;

@TestConfiguration(proxyBeanMethods = false)
public class SystemTestSeedConfiguration {

    private static final String STUDENT_USERNAME = "alumno.demo";
    private static final String INSTALLMENT_AMOUNT = "80000.00";
    private static final int DAYS_UNTIL_DUE = 30;
    private static final int OPEN_INSTALLMENTS = 2;

    @Bean
    ApplicationRunner systemTestSeedRunner(
            LocalSeedRunner demoAccountsSeed,
            PersonRepository personRepository,
            InstitutionRepository institutionRepository,
            StudentRepository studentRepository,
            StudentCurrentAccountService accountService,
            InvoiceRepository invoiceRepository,
            InvoiceNumberGenerator numberGenerator,
            TransactionTemplate transactionTemplate) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments arguments) {
                demoAccountsSeed.run(arguments);
                transactionTemplate.executeWithoutResult(status -> issueOpenInstallmentToDemoStudent());
            }

            private void issueOpenInstallmentToDemoStudent() {
                Person person = personRepository.findByUsername(STUDENT_USERNAME).orElseThrow();
                Institution institution = institutionRepository.findAll().stream().findFirst().orElseGet(this::createInstitution);
                Student student = new Student();
                student.setPerson(person);
                student.setInstitution(institution);
                student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
                Long studentId = studentRepository.save(student).getId();
                Long accountId = accountService.findOrOpenAndLockStudentCurrentAccount(studentId, Currency.ARS).getId();
                for (int installment = 1; installment <= OPEN_INSTALLMENTS; installment++) {
                    issueInstallment(accountId, studentId, installment);
                }
            }

            private void issueInstallment(Long accountId, Long studentId, int installment) {
                LocalDate today = LocalDate.now();
                InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.ADMINISTRATIVE_FEE,
                        InvoiceIssuingReason.AD_HOC_CHARGE, null);
                InvoiceLineDraft line = new InvoiceLineDraft(FeeType.ADMINISTRATIVE_FEE, "Cuota de prueba", YearMonth.from(today), null, null, null, null, null,
                        "E2E:" + studentId + ":" + installment, NonNegativeMoney.of(new BigDecimal(INSTALLMENT_AMOUNT), Currency.ARS));
                Invoice draft = InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, ACTOR);
                draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), today, today.plusDays(DAYS_UNTIL_DUE), SNAPSHOT, ACTOR);
                Invoice saved = invoiceRepository.save(draft);
                accountService.recordInvoiceIssuedDebit(accountService.lockStudentCurrentAccount(accountId), saved, today);
            }

            private Institution createInstitution() {
                Institution institution = new Institution();
                institution.setName("Instituto E2E");
                return institutionRepository.save(institution);
            }
        };
    }
}
