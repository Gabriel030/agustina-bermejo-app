package com.red_escuela.backend.billing.payment.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.billing.operation.AuthorizationRole;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SlowTest(reason = "Runs the authorization inbox, descriptor and decision-security scenarios over HTTP and its time includes the one-time Spring context start-up")
class AuthorizationRequestInboxIT extends AbstractRefundIT {

    private static final LocalDate EARLY_MARCH = LocalDate.of(2027, 3, 1);

    @Autowired
    AuthorizationRequestRepository authorizationRequests;

    private final List<Long> seededRequestIds = new ArrayList<>();

    @AfterEach
    void removeSeededRequests() {
        seededRequestIds.forEach(id -> {
            jdbc.update("delete from authorization_request_status_change where authorization_request_id = ?", id);
            jdbc.update("delete from authorization_request where id = ?", id);
        });
        seededRequestIds.clear();
    }

    private long refundRequestedBy(String administrativeToken) throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        MvcResult result = mockMvc.perform(requestOnBehalf(debtor, administrativeToken, "40000.00")).andExpect(status().isAccepted()).andReturn();
        return bodyOf(result).get("authorizationRequestId").asLong();
    }

    private long seededRequest(Person requester, BillingOperationType type, LocalDate expiresOn) {
        long id = inTransaction(() -> authorizationRequests.save(AuthorizationRequest.open(type, "{}", AuthorizationRole.DIRECTOR, "Pedido sembrado " + type, entityManager.find(Person.class, requester.getId()),
                expiresOn, Instant.now())).getId());
        seededRequestIds.add(id);
        return id;
    }

    private int statusOf(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
    }

    private int decisionStatus(String action, long requestId, String token) throws Exception {
        return mockMvc.perform(decision(action, requestId, token)).andReturn().getResponse().getStatus();
    }

    private JsonNode jsonOf(String path, String token) throws Exception {
        return bodyOf(mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn());
    }

    private List<Long> idsOf(JsonNode page) {
        return StreamSupport.stream(page.get("items").spliterator(), false).map(item -> item.get("id").asLong()).toList();
    }

    @Test
    @Tag("D-221")
    void theDirectorListsTheRequestsOfEveryoneAndTheAdministrativeOnlyHisOwn() throws Exception {
        Person administrativeA = newStaffPerson();
        Person administrativeB = newStaffPerson();
        String tokenA = tokenOf(administrativeA, "ADMINISTRATIVO");
        String tokenB = tokenOf(administrativeB, "ADMINISTRATIVO");
        long requestA = refundRequestedBy(tokenA);
        long requestB = refundRequestedBy(tokenB);
        String newestFirst = "/authorization-requests?size=50&sort=REQUESTED_AT&descending=true";

        List<Long> seenByDirector = idsOf(jsonOf(newestFirst, staffToken("DIRECTOR")));
        JsonNode seenByA = jsonOf(newestFirst, tokenA);
        JsonNode askingForB = jsonOf(newestFirst + "&requestedBy=" + administrativeB.getId(), tokenA);

        assertThat(seenByDirector).contains(requestA, requestB);
        assertThat(seenByDirector.indexOf(requestB)).isLessThan(seenByDirector.indexOf(requestA));
        assertThat(idsOf(seenByA)).contains(requestA).doesNotContain(requestB);
        assertThat(seenByA.get("items").findValues("requestedBy").stream().map(person -> person.get("id").asLong()).distinct()).containsExactly(administrativeA.getId());
        assertThat(idsOf(askingForB)).doesNotContain(requestB);
        assertThat(idsOf(jsonOf("/authorization-requests?requestedBy=" + administrativeB.getId(), staffToken("DIRECTOR")))).containsExactly(requestB);
    }

    @Test
    @Tag("D-221")
    void theListFiltersByStatusFamilyAndTypeAndSortsByExpiryWithTheUndatedLast() throws Exception {
        Person requester = newStaffPerson();
        long refund = seededRequest(requester, BillingOperationType.ISSUE_REFUND, null);
        long cancellation = seededRequest(requester, BillingOperationType.CANCEL_INVOICE, LocalDate.of(2031, 5, 1));
        long configuration = seededRequest(requester, BillingOperationType.CHANGE_BILLING_CONFIGURATION, LocalDate.of(2031, 4, 1));
        String director = staffToken("DIRECTOR");
        String mine = "/authorization-requests?requestedBy=" + requester.getId();

        assertThat(idsOf(jsonOf(mine, director))).containsExactly(configuration, cancellation, refund);
        assertThat(idsOf(jsonOf(mine + "&descending=true", director))).containsExactly(cancellation, configuration, refund);
        assertThat(idsOf(jsonOf(mine + "&family=INVOICES_AND_NOTES", director))).containsExactly(cancellation);
        assertThat(idsOf(jsonOf(mine + "&family=REFUNDS&family=CONFIGURATION", director))).containsExactly(configuration, refund);
        assertThat(idsOf(jsonOf(mine + "&operationType=CANCEL_INVOICE&family=REFUNDS", director))).isEmpty();
        assertThat(idsOf(jsonOf(mine + "&operationType=CANCEL_INVOICE&operationType=ISSUE_REFUND", director))).containsExactly(cancellation, refund);
        assertThat(idsOf(jsonOf(mine + "&status=WITHDRAWN", director))).isEmpty();
        assertThat(idsOf(jsonOf(mine + "&status=PENDING&status=APPROVED", director))).containsExactly(configuration, cancellation, refund);
        JsonNode secondPage = jsonOf(mine + "&size=2&page=1", director);
        assertThat(idsOf(secondPage)).containsExactly(refund);
        assertThat(secondPage.get("totalElements").asLong()).isEqualTo(3);
        assertThat(secondPage.get("page").asInt()).isEqualTo(1);
        assertThat(secondPage.get("size").asInt()).isEqualTo(2);
    }

    @Test
    @Tag("D-221")
    void aRequestWhoseOperationHasNoSpecificDescriptionGetsTheGenericOneWithoutExposingThePayload() throws Exception {
        Person requester = newStaffPerson();
        long unreadable = seededRequest(requester, BillingOperationType.ISSUE_REFUND, null);
        long undescribed = seededRequest(requester, BillingOperationType.REGISTER_CHARGEBACK, null);
        String director = staffToken("DIRECTOR");

        for (long id : List.of(unreadable, undescribed)) {
            JsonNode detail = jsonOf("/authorization-requests/" + id, director);
            assertThat(detail.get("detailBlock").get("type").asText()).isEqualTo("NONE");
            assertThat(detail.get("effectLines")).isEmpty();
            assertThat(detail.get("requestLines").findValues("label")).extracting(JsonNode::asText).containsExactly("Operación", "Motivo");
            assertThat(detail.get("request").get("studentName").isNull()).isTrue();
            assertThat(detail.get("request").get("amount").isNull()).isTrue();
            assertThat(detail.toString()).doesNotContain("operationPayload");
        }
        assertThat(jsonOf("/authorization-requests/" + undescribed, director).get("request").get("summary").asText()).isEqualTo("Contracargo");
        assertThat(jsonOf("/authorization-requests/" + undescribed, director).get("request").get("familyLabel").asText()).isEqualTo("Pagos y contracargos");
    }

    @Test
    @Tag("D-221")
    @Tag("D-216")
    void theRefundDetailDescribesTheRequestWithItsPartsAndTheEffectOnlyWhilePendingAndKeepsTheHistory() throws Exception {
        Person administrative = newStaffPerson();
        String administrativeToken = tokenOf(administrative, "ADMINISTRATIVO");
        long requestId = refundRequestedBy(administrativeToken);
        Person director = newStaffPerson();
        String directorToken = tokenOf(director, "DIRECTOR");

        JsonNode pending = jsonOf("/authorization-requests/" + requestId, directorToken);

        JsonNode request = pending.get("request");
        assertThat(request.get("operationType").asText()).isEqualTo("ISSUE_REFUND");
        assertThat(request.get("family").asText()).isEqualTo("REFUNDS");
        assertThat(request.get("status").asText()).isEqualTo("PENDING");
        assertThat(request.get("requiredRole").asText()).isEqualTo("DIRECTOR");
        assertThat(request.get("requestedBy").get("id").asLong()).isEqualTo(administrative.getId());
        assertThat(request.get("requestedBy").get("name").asText()).isNotBlank();
        assertThat(request.get("requestedBy").get("roles")).extracting(JsonNode::asText).contains("ADMINISTRATIVO");
        assertThat(request.get("studentName").asText()).isNotBlank();
        assertThat(decimalOf(request.get("amount"))).isEqualByComparingTo("40000.00");
        assertThat(request.get("summary").asText()).isEqualTo("Devolver saldo a favor, 1 pago");
        assertThat(pending.get("reason").asText()).isNotBlank();
        assertThat(pending.get("requestLines").findValues("label")).extracting(JsonNode::asText).containsExactly("Alumno", "Pedido", "Destino");
        assertThat(pending.get("requestLines").get(1).get("value").asText()).isEqualTo("Reembolso de $40.000");
        assertThat(pending.get("requestLines").get(2).get("value").asText()).isEqualTo(REQUEST_ACCOUNT + " · " + REQUEST_HOLDER);
        assertThat(pending.get("effectLines")).hasSize(3);
        assertThat(pending.get("effectLines").get(0).get("tone").asText()).isEqualTo("SUCCESS");
        JsonNode block = pending.get("detailBlock");
        assertThat(block.get("type").asText()).isEqualTo("REFUND_PARTS");
        assertThat(block.get("refundParts")).hasSize(1);
        assertThat(block.get("refundParts").get(0).get("status").asText()).isEqualTo("REQUESTED");
        assertThat(block.get("refundParts").get(0).get("methodType").asText()).isEqualTo("CASH");
        assertThat(decimalOf(block.get("refundParts").get(0).get("amount"))).isEqualByComparingTo("40000.00");
        assertThat(pending.get("statusChanges")).hasSize(1);
        assertThat(pending.get("statusChanges").get(0).get("toStatus").asText()).isEqualTo("PENDING");
        assertThat(pending.get("statusChanges").get(0).get("actorPersonId").asLong()).isEqualTo(administrative.getId());

        mockMvc.perform(decision("approve", requestId, directorToken)).andExpect(status().isOk());
        JsonNode approved = jsonOf("/authorization-requests/" + requestId, administrativeToken);

        assertThat(approved.get("request").get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("request").get("resumeOutcome").asText()).isEqualTo("PROCESSED");
        assertThat(approved.get("effectLines")).isEmpty();
        assertThat(approved.get("detailBlock").get("refundParts").get(0).get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("decidedBy").get("id").asLong()).isEqualTo(director.getId());
        assertThat(approved.get("decisionReason").asText()).isEqualTo("decisión de prueba");
        assertThat(approved.get("statusChanges")).hasSize(2);
        assertThat(approved.get("statusChanges").get(1).get("toStatus").asText()).isEqualTo("APPROVED");
    }

    @Test
    @Tag("D-221")
    void theDetailAndTheListAreForbiddenToStudentsAndToAdministrativesAskingForSomeoneElsesRequest() throws Exception {
        Person owner = newStaffPerson();
        String ownerToken = tokenOf(owner, "ADMINISTRATIVO");
        long requestId = refundRequestedBy(ownerToken);
        Debtor student = debtor();
        String detail = "/authorization-requests/" + requestId;

        assertThat(statusOf(detail, studentTokenOf(student))).isEqualTo(403);
        assertThat(statusOf("/authorization-requests", studentTokenOf(student))).isEqualTo(403);
        assertThat(statusOf(detail, staffToken("DOCENTE"))).isEqualTo(403);
        assertThat(statusOf("/authorization-requests", staffToken("PRECEPTOR"))).isEqualTo(403);
        assertThat(statusOf(detail, staffToken("ADMINISTRATIVO"))).isEqualTo(403);
        assertThat(statusOf(detail, ownerToken)).isEqualTo(200);
        assertThat(statusOf(detail, staffToken("DIRECTOR"))).isEqualTo(200);
        assertThat(statusOf("/authorization-requests/999999999", staffToken("DIRECTOR"))).isEqualTo(404);
    }

    @Test
    @Tag("D-221")
    @Tag("D-176")
    void onlyTheDirectorDecidesAndNobodyElseNotEvenTheAdministrativeWhoRequested() throws Exception {
        Person owner = newStaffPerson();
        String ownerToken = tokenOf(owner, "ADMINISTRATIVO");
        long requestId = refundRequestedBy(ownerToken);
        String studentToken = studentTokenOf(debtor());
        List<String> notDirectors = List.of(ownerToken, staffToken("ADMINISTRATIVO"), staffToken("PRECEPTOR"), staffToken("DOCENTE"), studentToken);

        for (String action : List.of("approve", "reject")) {
            for (String token : notDirectors) {
                assertThat(decisionStatus(action, requestId, token)).as(action).isEqualTo(403);
            }
        }

        assertThat(authorizationStatus(requestId)).isEqualTo("PENDING");
        assertThat(decisionStatus("approve", requestId, staffToken("DIRECTOR"))).isEqualTo(200);
        assertThat(authorizationStatus(requestId)).isEqualTo("APPROVED");
    }

    @Test
    @Tag("D-225")
    void onlyTheRequesterWithdrawsHisPendingRequestNotEvenTheDirector() throws Exception {
        Person owner = newStaffPerson();
        String ownerToken = tokenOf(owner, "ADMINISTRATIVO");
        long requestId = refundRequestedBy(ownerToken);

        assertThat(decisionStatus("withdraw", requestId, staffToken("DIRECTOR"))).isEqualTo(403);
        assertThat(decisionStatus("withdraw", requestId, staffToken("ADMINISTRATIVO"))).isEqualTo(403);
        assertThat(authorizationStatus(requestId)).isEqualTo("PENDING");
        assertThat(decisionStatus("withdraw", requestId, ownerToken)).isEqualTo(200);
        assertThat(authorizationStatus(requestId)).isEqualTo("WITHDRAWN");
    }

    @Test
    @Tag("D-221")
    void aDirectorCannotDecideARequestHeRequestedHimselfButAnotherDirectorCan() throws Exception {
        Person requestingDirector = newStaffPerson();
        String requestingToken = tokenOf(requestingDirector, "DIRECTOR");
        long approvable = seededRequest(requestingDirector, BillingOperationType.CANCEL_INVOICE, null);
        long rejectable = seededRequest(requestingDirector, BillingOperationType.CANCEL_INVOICE, null);

        assertThat(decisionStatus("approve", approvable, requestingToken)).isEqualTo(403);
        assertThat(decisionStatus("reject", rejectable, requestingToken)).isEqualTo(403);
        assertThat(authorizationStatus(approvable)).isEqualTo("PENDING");
        assertThat(authorizationStatus(rejectable)).isEqualTo("PENDING");
        assertThat(decisionStatus("reject", rejectable, staffToken("DIRECTOR"))).isEqualTo(200);
        assertThat(authorizationStatus(rejectable)).isEqualTo("REJECTED");
    }

    @Test
    @Tag("D-221")
    @Tag("D-216")
    void aRefundRequestedByTheDirectorRunsRightAwayAndOpensNoAuthorizationRequest() throws Exception {
        Person director = newStaffPerson();
        String directorToken = tokenOf(director, "DIRECTOR");
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);

        MvcResult result = mockMvc.perform(requestOnBehalf(debtor, directorToken, "40000.00")).andExpect(status().isCreated()).andReturn();

        assertThat(bodyOf(result).get("authorizationRequestId").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from authorization_request where requested_by_id = ?", Long.class, director.getId())).isZero();
        assertThat(idsOf(jsonOf("/authorization-requests?requestedBy=" + director.getId(), directorToken))).isEmpty();
    }
}
