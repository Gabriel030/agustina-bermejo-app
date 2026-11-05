package com.red_escuela.backend.billing.payment.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.payment.domain.BankTransferPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.CardPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.CashPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.GatewayRawStatus;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.billing.operation.CardPaymentType;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

abstract class AbstractRefundIT extends AbstractBankTransferReceiptHttpIT {

    static final String ORIGIN_ACCOUNT = "0170099220000067797370";
    static final String ORIGIN_HOLDER = "Titular del Comprobante";
    static final String REQUEST_ACCOUNT = "alias.del.pedido";
    static final String REQUEST_HOLDER = "Titular del Pedido";

    @Autowired
    PaymentRepository paymentRepository;

    BillingInvariantChecker invariants;

    @BeforeEach
    void takeTheInvariantBaselineBeforeAnyScenario() {
        invariants = BillingInvariantChecker.startingNow(jdbc);
    }

    long cashPayment(Debtor debtor, String amount, LocalDate paidOn) {
        return inTransaction(() -> {
            StudentCurrentAccount account = service.lockStudentCurrentAccount(debtor.accountId());
            Payment payment = paymentRepository.save(Payment.registerApprovedCashPayment(account, NonNegativeMoney.of(ars(amount)), paidOn, new CashPaymentDetails("R-" + nextKey()),
                    nextKey(), StatusChangeActor.ofPerson(staff.getId())));
            service.recordPaymentReceivedCredit(account, payment.getId(), NonNegativeMoney.of(ars(amount)), paidOn, "Pago en efectivo");
            return payment.getId();
        });
    }

    long transferPayment(Debtor debtor, String amount, LocalDate paidOn) {
        return inTransaction(() -> {
            StudentCurrentAccount account = service.lockStudentCurrentAccount(debtor.accountId());
            BankTransferPaymentDetails details = new BankTransferPaymentDetails("Banco Nación", ORIGIN_ACCOUNT, nextOperationNumber(), ORIGIN_HOLDER, paidOn);
            Payment payment = Payment.createPendingBankTransferPayment(account, NonNegativeMoney.of(ars(amount)), details, nextKey(), StatusChangeActor.ofPerson(staff.getId()));
            payment.approvePayment(NonNegativeMoney.of(ars(amount)), paidOn, StatusChangeActor.ofPerson(staff.getId()), null);
            payment = paymentRepository.save(payment);
            service.recordPaymentReceivedCredit(account, payment.getId(), NonNegativeMoney.of(ars(amount)), paidOn, "Pago por transferencia");
            return payment.getId();
        });
    }

    long onlinePayment(Debtor debtor, String amount, LocalDate paidOn) {
        return inTransaction(() -> {
            StudentCurrentAccount account = service.lockStudentCurrentAccount(debtor.accountId());
            Payment payment = paymentRepository.save(Payment.registerApprovedGatewayPayment(account, NonNegativeMoney.of(ars(amount)), PaymentGatewayCode.MERCADO_PAGO,
                    new CardPaymentDetails(CardPaymentType.CREDIT, 1, "4242"), "mp-" + nextKey(), new GatewayRawStatus("approved", "accredited"), paidOn,
                    StatusChangeActor.ofPerson(staff.getId())));
            service.recordPaymentReceivedCredit(account, payment.getId(), NonNegativeMoney.of(ars(amount)), paidOn, "Pago online");
            return payment.getId();
        });
    }

    MockHttpServletRequestBuilder refundRequest(String path, String token, String amount, String account, String holder) {
        String destination = (account == null ? "" : ",\"destinationAccount\":\"" + account + "\"") + (holder == null ? "" : ",\"destinationHolder\":\"" + holder + "\"");
        return post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":{\"amount\":" + amount + ",\"currency\":\"ARS\"}" + destination + "}");
    }

    MockHttpServletRequestBuilder requestOnBehalf(Debtor debtor, String token, String amount) {
        return refundRequest("/students/" + debtor.studentId() + "/refund-requests", token, amount, REQUEST_ACCOUNT, REQUEST_HOLDER);
    }

    MockHttpServletRequestBuilder completion(long refundId, String token, String completedOn, String reference, String method) {
        return post("/refunds/{id}/completion", refundId).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"completedOn\":\"" + completedOn + "\",\"reference\":" + (reference == null ? "null" : "\"" + reference + "\"") + ",\"methodType\":\"" + method + "\"}");
    }

    MockHttpServletRequestBuilder failure(long refundId, String token, String reason) {
        return post("/refunds/{id}/failure", refundId).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":" + (reason == null ? "null" : "\"" + reason + "\"") + "}");
    }

    MockHttpServletRequestBuilder decision(String action, long authorizationRequestId, String token) {
        return post("/authorization-requests/{id}/" + action, authorizationRequestId).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decisionReason\":\"decisión de prueba\"}");
    }

    java.math.BigDecimal decimalOf(JsonNode money) {
        return new java.math.BigDecimal(money.get("amount").asText());
    }

    String codeOf(MvcResult result) throws Exception {
        return bodyOf(result).get("code").asText();
    }

    List<Long> partIds(JsonNode response) {
        return response.get("parts").findValues("id").stream().map(JsonNode::asLong).toList();
    }

    Map<String, Object> refundRow(long refundId) {
        return jdbc.queryForMap("select * from refund where id = ?", refundId);
    }

    String refundStatus(long refundId) {
        return jdbc.queryForObject("select status from refund where id = ?", String.class, refundId);
    }

    long entriesOfType(Long accountId, String entryType) {
        return jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ? and entry_type = ?", Long.class, accountId, entryType);
    }

    java.math.BigDecimal unallocatedCredit(Long accountId) {
        return amount("select unallocated_credit_amount from student_current_account where id = ?", accountId);
    }

    String authorizationStatus(long authorizationRequestId) {
        return jdbc.queryForObject("select status from authorization_request where id = ?", String.class, authorizationRequestId);
    }

    BillingOperationType authorizationType(long authorizationRequestId) {
        return BillingOperationType.valueOf(jdbc.queryForObject("select operation_type from authorization_request where id = ?", String.class, authorizationRequestId));
    }
}
