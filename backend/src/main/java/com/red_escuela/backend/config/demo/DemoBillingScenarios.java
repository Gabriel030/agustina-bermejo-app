package com.red_escuela.backend.config.demo;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;
import com.red_escuela.backend.billing.discount.operation.AcquireCohortPromotionCommand;
import com.red_escuela.backend.billing.discount.operation.CreateDiscountDefinitionCommand;
import com.red_escuela.backend.billing.discount.operation.GrantStudentDiscountCommand;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawal;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.RejectPaymentReceiptUseCase;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.billing.note.domain.CreditNoteDestination;
import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.domain.IssueCreditNoteCommand;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.BillingOperations;
import com.red_escuela.backend.billing.payment.domain.PaymentMethodType;
import com.red_escuela.backend.billing.payment.operation.ApprovePendingBankTransferPaymentCommand;
import com.red_escuela.backend.billing.payment.operation.CompleteRefundCommand;
import com.red_escuela.backend.billing.payment.operation.FailRefundCommand;
import com.red_escuela.backend.billing.payment.operation.IssueRefundCommand;
import com.red_escuela.backend.billing.operation.CardPaymentType;
import com.red_escuela.backend.billing.payment.domain.CardPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.GatewayRawStatus;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.operation.RegisterGatewayPaymentCommand;
import com.red_escuela.backend.billing.payment.operation.RegisterCashPaymentCommand;
import com.red_escuela.backend.billing.payment.operation.RegisterChargebackCommand;
import com.red_escuela.backend.billing.payment.operation.RejectPendingBankTransferPaymentCommand;
import com.red_escuela.backend.billing.payment.operation.ReverseChargebackCommand;
import com.red_escuela.backend.billing.payment.operation.SubmitBankTransferReceiptCommand;
import com.red_escuela.backend.billing.payment.service.CashPaymentPreviewService;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Component
@Profile("local & demo")
public class DemoBillingScenarios {

    private static final Logger log = LoggerFactory.getLogger(DemoBillingScenarios.class);
    private static final String DESTINATION_ACCOUNT = "0170099220000067890123";

    private final JdbcTemplate jdbc;
    private final ApprovePaymentReceiptUseCase approveReceipt;
    private final RejectPaymentReceiptUseCase rejectReceipt;
    private final PersonRepository personRepository;
    private final String uploadsDirectory;
    private final DemoClock clock;
    private final TransactionTemplate transactions;
    private final BillingOperations operations;
    private final BillingOperationPipeline pipeline;
    private final CashPaymentPreviewService cashPreview;
    private final ProgramEnrollmentService programEnrollmentService;

    public DemoBillingScenarios(@org.springframework.beans.factory.annotation.Value("${app.uploads.directory}") String uploadsDirectory, JdbcTemplate jdbc, DemoClock clock, TransactionTemplate transactions, BillingOperations operations,
                                BillingOperationPipeline pipeline, CashPaymentPreviewService cashPreview,
                                ProgramEnrollmentService programEnrollmentService, ApprovePaymentReceiptUseCase approveReceipt,
                                RejectPaymentReceiptUseCase rejectReceipt, PersonRepository personRepository) {
        this.approveReceipt = approveReceipt;
        this.rejectReceipt = rejectReceipt;
        this.personRepository = personRepository;
        this.programEnrollmentService = programEnrollmentService;
        this.uploadsDirectory = uploadsDirectory;
        this.jdbc = jdbc;
        this.clock = clock;
        this.transactions = transactions;
        this.operations = operations;
        this.pipeline = pipeline;
        this.cashPreview = cashPreview;
    }

    private List<Long> billedStudents(List<Long> students, boolean withOpenInvoices) {
        String condition = withOpenInvoices ? "and i.outstanding_amount > 0 and i.document_type = 'INVOICE'" : "";
        return students.stream().filter(id -> jdbc.queryForObject("select count(*) from invoice i join student_current_account a on a.id = i.student_current_account_id "
                + "where a.student_id = ? " + condition, Long.class, id) > 0).toList();
    }

    public void seed(List<Long> neverPayAll, List<Long> stoppedAll, List<Long> currentAll, Long directorId, Long administrativeId) {
        List<Long> neverPay = billedStudents(neverPayAll, true);
        List<Long> stopped = billedStudents(stoppedAll, true);
        List<Long> current = billedStudents(currentAll, false);
        StatusChangeActor admin = new StatusChangeActor(administrativeId, null);
        StatusChangeActor director = new StatusChangeActor(directorId, null);

        attempt("transferencia pendiente de revision", () -> submitTransfer(neverPay.get(0), "TRF-DEMO-0001"));
        attemptAs("transferencia aprobada", String.valueOf(administrativeId), "ADMINISTRATIVO", () -> {
            submitTransfer(neverPay.get(1), "TRF-DEMO-0002");
            Long paymentId = lastId("payment", neverPay.get(1));
            Long receiptId = jdbc.queryForObject("select id from bank_transfer_receipt where payment_id = ?", Long.class, paymentId);
            transactions.executeWithoutResult(status -> approveReceipt.approve(receiptId, personRepository.findById(administrativeId).orElseThrow(), PaymentVerification.none()));
        });
        attemptAs("transferencia rechazada", String.valueOf(administrativeId), "ADMINISTRATIVO", () -> {
            submitTransfer(neverPay.get(2), "TRF-DEMO-0003");
            Long paymentId = lastId("payment", neverPay.get(2));
            Long receiptId = jdbc.queryForObject("select id from bank_transfer_receipt where payment_id = ?", Long.class, paymentId);
            transactions.executeWithoutResult(status -> rejectReceipt.reject(receiptId, "El comprobante no coincide con el monto de la cuota", personRepository.findById(administrativeId).orElseThrow()));
        });

        for (int i = 0; i < 6; i++) {
            Long studentId = current.get(i);
            attempt("saldo a favor " + studentId, () -> payCash(studentId, new BigDecimal("80000"), admin));
        }
        attemptAs("reembolso pendiente de autorizacion (administrativo)", String.valueOf(administrativeId), "ADMINISTRATIVO", () -> issueRefund(current.get(0), administrativeId, false));
        attemptAs("reembolso emitido por el director", String.valueOf(directorId), "DIRECTOR", () -> issueRefund(current.get(1), directorId, true));
        attemptAs("reembolso completado", String.valueOf(directorId), "DIRECTOR", () -> {
            issueRefund(current.get(2), directorId, true);
            Long refundId = lastId("refund", current.get(2));
            run(BillingOperationType.COMPLETE_REFUND,
                    new CompleteRefundCommand(refundId, LocalDate.now(clock), "TRF-REEMB-0001", PaymentMethodType.CASH, admin));
        });
        attemptAs("reembolso fallido", String.valueOf(directorId), "DIRECTOR", () -> {
            issueRefund(current.get(3), directorId, true);
            Long refundId = lastId("refund", current.get(3));
            run(BillingOperationType.FAIL_REFUND, new FailRefundCommand(refundId, "La cuenta de destino fue rechazada por el banco", admin));
        });

        attemptAs("pedido de cancelacion de cuota (administrativo)", String.valueOf(administrativeId), "ADMINISTRATIVO", () -> {
            Long invoiceId = (Long) oldestOpenInvoice(stopped.get(0), "OVERDUE").get("id");
            run(BillingOperationType.CANCEL_INVOICE,
                    CancelInvoiceCommand.byPerson(invoiceId, CreditNoteReason.CANCELLATION, "Alta duplicada, la cuota no corresponde", administrativeId, false));
        });
        attemptAs("pedido de nota de credito (administrativo)", String.valueOf(administrativeId), "ADMINISTRATIVO", () -> {
            Long invoiceId = (Long) oldestOpenInvoice(stopped.get(1), "OVERDUE").get("id");
            run(BillingOperationType.ISSUE_CREDIT_NOTE, new IssueCreditNoteCommand(invoiceId, CreditNoteReason.BILLING_ERROR,
                    "Importe cargado de mas en la cuota", List.of(new CreditNoteLineAmount(1, Money.of(new BigDecimal("10000"), Currency.ARS))),
                    CreditNoteDestination.DEBT_OFFSET, administrativeId, false, null));
        });

        attemptAs("nota de credito emitida por el director", String.valueOf(directorId), "DIRECTOR", () -> {
            Long invoiceId = (Long) oldestOpenInvoice(stopped.get(2), "OVERDUE").get("id");
            run(BillingOperationType.ISSUE_CREDIT_NOTE, new IssueCreditNoteCommand(invoiceId, CreditNoteReason.BILLING_ERROR,
                    "Error de facturacion: importe duplicado en la cuota", List.of(new CreditNoteLineAmount(1, Money.of(new BigDecimal("15000"), Currency.ARS))),
                    CreditNoteDestination.DEBT_OFFSET, directorId, true, null));
        });
        attempt("pago online simulado de Mercado Pago", () -> {
            Long studentId = stopped.get(stopped.size() - 1);
            Map<String, Object> invoice = jdbc.queryForMap("select i.id, i.student_current_account_id, i.outstanding_amount from invoice i "
                    + "join student_current_account a on a.id = i.student_current_account_id where a.student_id = ? and i.document_type = 'INVOICE' "
                    + "and i.outstanding_amount > 0 order by i.due_date desc, i.id desc limit 1", studentId);
            Long personId = jdbc.queryForObject("select person_id from student where id = ?", Long.class, studentId);
            run(BillingOperationType.REGISTER_GATEWAY_PAYMENT, new RegisterGatewayPaymentCommand((Long) invoice.get("student_current_account_id"), (Long) invoice.get("id"),
                    java.util.Set.of(), Money.of((BigDecimal) invoice.get("outstanding_amount"), Currency.ARS), PaymentGatewayCode.MERCADO_PAGO,
                    new CardPaymentDetails(CardPaymentType.CREDIT, 1, "4242"), "MP-DEMO-0001", new GatewayRawStatus("approved", null),
                    StatusChangeActor.ofPerson(personId)));
        });
        attemptAs("contracargo con reversion", String.valueOf(directorId), "DIRECTOR", () -> {
            Long studentId = current.get(4);
            chargeBack(studentId, "CB-DEMO-0001", director);
            Long chargebackId = lastId("chargeback", studentId);
            run(BillingOperationType.REVERSE_CHARGEBACK, new ReverseChargebackCommand(chargebackId, director));
        });
        attemptAs("contracargo vigente", String.valueOf(directorId), "DIRECTOR", () -> chargeBack(current.get(5), "CB-DEMO-0002", director));
    }

    private void chargeBack(Long studentId, String reference, StatusChangeActor director) {
        Long paymentId = lastId("payment", studentId);
        BigDecimal amount = jdbc.queryForObject("select amount from payment where id = ?", BigDecimal.class, paymentId);
        run(BillingOperationType.REGISTER_CHARGEBACK,
                new RegisterChargebackCommand(paymentId, Money.of(amount, Currency.ARS), LocalDate.now(clock), reference, director));
    }

    private void attemptAs(String name, String username, String role, Runnable action) {
        attempt(name, () -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
            SecurityContextHolder.setContext(context);
            try {
                action.run();
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }

    private void attempt(String name, Runnable action) {
        try {
            action.run();
            log.info("Seed demo escenario ok: {}", name);
        } catch (RuntimeException e) {
            log.warn("Seed demo escenario fallido: {}: {}", name, e.toString());
        }
    }

    private Long lastId(String table, Long studentId) {
        String sql = switch (table) {
            case "payment" -> "select max(p.id) from payment p join student_current_account a on a.id = p.student_current_account_id where a.student_id = ?";
            case "chargeback" -> "select max(c.id) from chargeback c join payment p on p.id = c.payment_id "
                    + "join student_current_account a on a.id = p.student_current_account_id where a.student_id = ?";
            default -> "select max(r.id) from refund r join student_current_account a on a.id = r.student_current_account_id where a.student_id = ?";
        };
        return jdbc.queryForObject(sql, Long.class, studentId);
    }

    private Map<String, Object> oldestOpenInvoice(Long studentId, String paymentStatus) {
        return jdbc.queryForMap("select i.id, i.outstanding_amount from invoice i join student_current_account a on a.id = i.student_current_account_id "
                + "where a.student_id = ? and i.document_type = 'INVOICE' and i.payment_status in ('OVERDUE', 'PENDING') and i.outstanding_amount > 0 order by i.due_date, i.id limit 1",
                studentId);
    }

    private void payCash(Long studentId, BigDecimal amountValue, StatusChangeActor actor) {
        Money amount = Money.of(amountValue, Currency.ARS);
        transactions.executeWithoutResult(status -> {
            String fingerprint = cashPreview.previewCashPaymentOfStudent(studentId, amount).fingerprint();
            execute(BillingOperationType.REGISTER_PAYMENT, new RegisterCashPaymentCommand(studentId, amount, LocalDate.now(clock),
                    "REC-CREDITO-" + studentId, null, fingerprint, false, "demo-credit-" + studentId, actor));
        });
    }

    private void submitTransfer(Long studentId, String operationNumber) {
        Map<String, Object> invoice = oldestOpenInvoice(studentId, "OVERDUE");
        Money amount = Money.of((BigDecimal) invoice.get("outstanding_amount"), Currency.ARS);
        Long personId = jdbc.queryForObject("select person_id from student where id = ?", Long.class, studentId);
        run(BillingOperationType.SUBMIT_BANK_TRANSFER_RECEIPT, new SubmitBankTransferReceiptCommand(studentId, List.of((Long) invoice.get("id")), amount,
                LocalDate.now(clock), operationNumber, DESTINATION_ACCOUNT, "Titular " + studentId, "Banco Nacion", "demo-sha-" + operationNumber,
                id -> storeDemoReceipt(id, operationNumber), "demo-transfer-" + operationNumber, new StatusChangeActor(personId, null)));
    }

    private static final byte[] RECEIPT_PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private String storeDemoReceipt(Long studentId, String operationNumber) {
        String relative = "students/" + studentId + "/comprobantes_demo-" + operationNumber + ".png";
        try {
            java.nio.file.Path target = java.nio.file.Path.of(uploadsDirectory).resolve(relative);
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.write(target, RECEIPT_PNG);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return relative;
    }

    public void grantScholarship(Long studentId, Long directorId) {
        attemptAs("beca del 50% otorgada a un alumno", String.valueOf(directorId), "DIRECTOR", () -> {
            Long enrollmentId = jdbc.queryForObject("select id from program_enrollment where student_id = ? order by id limit 1", Long.class, studentId);
            run(BillingOperationType.GRANT_STUDENT_DISCOUNT, GrantStudentDiscountCommand.byPerson(EnrollmentReference.ofProgram(enrollmentId),
                    new StudentDiscountGrantRequest(DiscountType.SCHOLARSHIP, null, new MonetaryAdjustment.Proportional(Percentage.ofFraction(new BigDecimal("0.50"))), InvoiceType.MONTHLY_INSTALLMENT, YearMonth.of(2026, 9),
                            YearMonth.of(2027, 3), "Beca por rendimiento academico"), directorId, true));
        });
    }

    public void createCohortPromotion(Long directorId) {
        attemptAs("promocion de cohorte con cupo parcialmente usado", String.valueOf(directorId), "DIRECTOR", () -> {
            Map<String, Object> target = jdbc.queryForMap("select program_id, program_cohort_id from program_enrollment where program_cohort_id is not null order by id limit 1");
            Long programId = (Long) target.get("program_id");
            Long cohortId = (Long) target.get("program_cohort_id");
            run(BillingOperationType.CREATE_DISCOUNT_DEFINITION, CreateDiscountDefinitionCommand.byPerson(new DiscountDefinitionDraft("Matricula bonificada 20% - inscripcion temprana",
                    DiscountDefinitionType.COHORT_PROMOTION, InvoiceType.ENROLLMENT_FEE, new MonetaryAdjustment.Proportional(Percentage.ofFraction(new BigDecimal("0.20"))),
                    null, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 12, 31), 5, null, programId, cohortId, null, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 12, 31), 10),
                    directorId, true));
            List<Long> enrollmentIds = jdbc.queryForList("select id from program_enrollment where program_cohort_id = ? order by id limit 2", Long.class, cohortId);
            for (Long enrollmentId : enrollmentIds) {
                run(BillingOperationType.ACQUIRE_COHORT_PROMOTION, new AcquireCohortPromotionCommand(EnrollmentReference.ofProgram(enrollmentId),
                        new OfferingReference(programId, null), cohortId, LocalDate.of(2026, 2, 15), directorId));
            }
        });    }

    public void withdrawNeverPayer(List<Long> neverPayAll, LocalDate informedDate, Long administrativeId) {
        List<Long> neverPay = billedStudents(neverPayAll, true);
        attemptAs("alumno dado de baja con deuda", String.valueOf(administrativeId), "ADMINISTRATIVO", () -> {
            Long enrollmentId = jdbc.queryForObject("select id from program_enrollment where student_id = ? order by id limit 1", Long.class, neverPay.get(3));
            programEnrollmentService.withdraw(enrollmentId, EnrollmentWithdrawal.voluntaryRegisteredBy(informedDate, administrativeId));
        });
    }

    private void issueRefund(Long studentId, Long requesterId, boolean byDirector) {
        run(BillingOperationType.ISSUE_REFUND, new IssueRefundCommand(studentId, Money.of(new BigDecimal("50000"), Currency.ARS), DESTINATION_ACCOUNT,
                "Titular " + studentId, requesterId, byDirector, null));
    }

    private <C> void run(BillingOperationType type, C command) {
        transactions.executeWithoutResult(status -> execute(type, command));
    }

    private <C> void execute(BillingOperationType type, C command) {
        BillingOperation<C, Object> operation = operations.operationOf(type);
        pipeline.runOperation(operation, command);
    }
}
