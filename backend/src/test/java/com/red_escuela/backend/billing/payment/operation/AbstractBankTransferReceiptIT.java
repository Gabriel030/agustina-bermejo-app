package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.service.AbstractCurrentAccountIT;
import com.red_escuela.backend.billing.collections.service.OverdueInvoiceTransitionService;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.service.LatePaymentTestFixtures;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

abstract class AbstractBankTransferReceiptIT extends AbstractCurrentAccountIT {

    static final LocalDate TODAY = LocalDate.of(2027, 3, 9);
    static final LocalDate MARCH_DUE = LocalDate.of(2027, 3, 10);
    static final LocalDate APRIL_DUE = LocalDate.of(2027, 4, 10);

    @Autowired
    BillingOperationPipeline pipeline;
    @Autowired
    SubmitBankTransferReceiptOperation operation;
    @Autowired
    OverdueInvoiceTransitionService overdueTransition;
    @Autowired
    ConfigurationVersionRepository configurationVersionRepository;

    @Value("${app.uploads.directory}")
    String uploadsDirectory;
    final List<String> storedPaths = new CopyOnWriteArrayList<>();
    final AtomicInteger storedFiles = new AtomicInteger();
    final AtomicInteger keyCounter = new AtomicInteger();
    Person staff;
    StatusChangeActor actor;

    @BeforeEach
    void fixTheClockAndPublishTheLatePaymentTerms() {
        MutableClock.INSTANCE.setDate(TODAY);
        staff = newStaffPerson();
        actor = new StatusChangeActor(staff.getId(), null);
        LatePaymentTestFixtures.persistLatePayment(configurationVersionRepository, staff, 0, BigDecimal.valueOf(0.05), 30, 2, LocalDate.of(2027, 1, 1));
    }

    record Debtor(Long studentId, Long accountId) {
    }

    Debtor debtor() {
        Long studentId = newStudentId();
        return new Debtor(studentId, newAccountId(studentId, Currency.ARS));
    }

    Long invoiceOf(Debtor debtor, String amount, LocalDate dueDate) {
        return issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), amount, dueDate);
    }

    Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    String nextKey() {
        return "bt-key-" + keyCounter.incrementAndGet() + "-" + System.nanoTime();
    }

    SubmitBankTransferReceiptCommand command(Debtor debtor, List<Long> invoiceIds, String amount, LocalDate transferDate, String operationNumber, String key,
                                             String fileSha256) {
        return new SubmitBankTransferReceiptCommand(debtor.studentId(), invoiceIds, ars(amount), transferDate, operationNumber, "0170099220000067797370", "Ana Pérez",
                "Banco Nación", fileSha256, this::storeRealFile, key, actor);
    }

    String storeRealFile(Long studentId) {
        String relativePath = "students/" + studentId + "/comprobantes_" + storedFiles.incrementAndGet() + "_" + System.nanoTime() + ".pdf";
        try {
            Path file = Path.of(uploadsDirectory).resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "comprobante");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        storedPaths.add(relativePath);
        return relativePath;
    }

    long filesStillOnDisk() {
        return storedPaths.stream().filter(path -> Files.exists(Path.of(uploadsDirectory).resolve(path))).count();
    }

    SubmitBankTransferReceiptCommand command(Debtor debtor, List<Long> invoiceIds, String amount, LocalDate transferDate, String operationNumber) {
        return command(debtor, invoiceIds, amount, transferDate, operationNumber, nextKey(), "sha-" + operationNumber);
    }

    BankTransferReceiptSubmissionResult submit(SubmitBankTransferReceiptCommand command) {
        BillingOperationOutcome<BankTransferReceiptSubmissionResult> outcome = pipeline.runOperation(operation, command);
        return ((BillingOperationOutcome.Processed<BankTransferReceiptSubmissionResult>) outcome).result();
    }

    String paymentStatusOfInvoice(Long invoiceId) {
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId);
    }
}
