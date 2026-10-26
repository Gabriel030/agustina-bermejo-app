package com.red_escuela.backend.billing.authorization.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescriber;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DescriptionLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DetailBlock;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectTone;
import com.red_escuela.backend.billing.operation.AuthorizationRole;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.BillingOperations;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-221")
class AuthorizationRequestDescriptionAssemblerTest {

    record StubContext(String name) {
    }

    private final BillingOperations operations = mock(BillingOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    AuthorizationRequestDescriptionAssemblerTest() {
        BillingOperation<Object, Object> operation = mock(BillingOperation.class);
        when(operation.contextType()).thenReturn((Class) StubContext.class);
        when(operations.operationOf(any())).thenReturn(operation);
    }

    private AuthorizationRequest requestOf(BillingOperationType type, String payload) {
        Person requester = new Person();
        requester.setId(3L);
        return AuthorizationRequest.open(type, payload, AuthorizationRole.DIRECTOR, "Motivo del pedido", requester, null, Instant.parse("2026-10-20T10:00:00Z"));
    }

    private AuthorizationRequestDescriber<StubContext> describerOf(BillingOperationType type, java.util.function.Function<StubContext, AuthorizationRequestDescription> description) {
        return new AuthorizationRequestDescriber<>() {
            @Override
            public BillingOperationType type() {
                return type;
            }

            @Override
            public AuthorizationRequestDescription describe(StubContext context, Long authorizationRequestId) {
                return description.apply(context);
            }
        };
    }

    private AuthorizationRequestDescription full(StubContext context) {
        return new AuthorizationRequestDescription("Resumen de " + context.name(), "Alumno X", null, List.of(new DescriptionLine("Dato", "Valor")),
                List.of(new EffectLine(EffectTone.SUCCESS, "Pasa algo")), DetailBlock.change(List.of()));
    }

    @Test
    void aPendingRequestKeepsEveryPartOfTheDescriptionOfItsOperation() {
        AuthorizationRequestDescriptionAssembler assembler = new AuthorizationRequestDescriptionAssembler(
                List.of(describerOf(BillingOperationType.CANCEL_INVOICE, this::full)), operations, objectMapper);

        AuthorizationRequestDescription description = assembler.describe(requestOf(BillingOperationType.CANCEL_INVOICE, "{\"name\":\"factura\"}"));

        assertThat(description.summary()).isEqualTo("Resumen de factura");
        assertThat(description.effectLines()).hasSize(1);
        assertThat(description.detailBlock().type()).isEqualTo(AuthorizationRequestDescription.DetailBlockType.CHANGE);
    }

    @Test
    void aDecidedRequestKeepsWhatWasAskedButNoLongerPromisesAnEffect() {
        AuthorizationRequestDescriptionAssembler assembler = new AuthorizationRequestDescriptionAssembler(
                List.of(describerOf(BillingOperationType.CANCEL_INVOICE, this::full)), operations, objectMapper);
        AuthorizationRequest request = requestOf(BillingOperationType.CANCEL_INVOICE, "{\"name\":\"factura\"}");
        Person decider = new Person();
        decider.setId(4L);
        request.reject(decider, "No corresponde", Instant.parse("2026-10-21T10:00:00Z"));

        AuthorizationRequestDescription description = assembler.describe(request);

        assertThat(description.summary()).isEqualTo("Resumen de factura");
        assertThat(description.requestLines()).hasSize(1);
        assertThat(description.studentName()).isEqualTo("Alumno X");
        assertThat(description.effectLines()).isEmpty();
    }

    @Test
    void anOperationWithoutDescriberGetsTheGenericDescriptionMadeOfItsLabelAndReason() {
        AuthorizationRequestDescriptionAssembler assembler = new AuthorizationRequestDescriptionAssembler(List.of(), operations, objectMapper);

        AuthorizationRequestDescription description = assembler.describe(requestOf(BillingOperationType.REGISTER_CHARGEBACK, "{\"secreto\":\"no se muestra\"}"));

        assertThat(description.summary()).isEqualTo("Contracargo");
        assertThat(description.requestLines()).containsExactly(new DescriptionLine("Operación", "Contracargo"), new DescriptionLine("Motivo", "Motivo del pedido"));
        assertThat(description.effectLines()).isEmpty();
        assertThat(description.detailBlock().type()).isEqualTo(AuthorizationRequestDescription.DetailBlockType.NONE);
        assertThat(description.toString()).doesNotContain("secreto");
    }

    @Test
    void aPayloadThatCannotBeReadOrADescriberThatFailsFallsBackToTheGenericDescription() {
        AuthorizationRequestDescriptionAssembler failing = new AuthorizationRequestDescriptionAssembler(List.of(describerOf(BillingOperationType.CANCEL_INVOICE, context -> {
            throw new IllegalStateException("boom");
        })), operations, objectMapper);

        assertThat(failing.describe(requestOf(BillingOperationType.CANCEL_INVOICE, "{\"name\":\"x\"}")).summary()).isEqualTo("Cancelación de factura");
        assertThat(failing.describe(requestOf(BillingOperationType.CANCEL_INVOICE, "{\"name\":")).summary()).isEqualTo("Cancelación de factura");
        assertThat(failing.describe(requestOf(BillingOperationType.CANCEL_INVOICE, "{\"unknown\":1}")).summary()).isEqualTo("Cancelación de factura");
    }

    @Test
    void twoDescribersForTheSameOperationAreRejectedAtStartup() {
        List<AuthorizationRequestDescriber<?>> twice = List.of(describerOf(BillingOperationType.CANCEL_INVOICE, this::full), describerOf(BillingOperationType.CANCEL_INVOICE, this::full));

        assertThatThrownBy(() -> new AuthorizationRequestDescriptionAssembler(twice, operations, objectMapper)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCEL_INVOICE");
    }
}
