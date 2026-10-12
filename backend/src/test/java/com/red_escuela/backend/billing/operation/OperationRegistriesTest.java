package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.billing.operation.RecordingOperationFixtures.ProbeContext;
import com.red_escuela.backend.billing.operation.RecordingOperationFixtures.RecordingOperation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationRegistriesTest {
    private static final BillingOperationType TYPE = BillingOperationType.CREATE_LATE_PAYMENT_SETTINGS;

    @Test
    void handlersAreReturnedInDeclaredOrderForTheirOperationType() {
        PreProcessingHandler<ProbeContext> first = (type, context, prior) -> PreProcessingResult.proceed();
        PreProcessingHandler<ProbeContext> second = (type, context, prior) -> PreProcessingResult.proceed();

        OperationHandlerRegistry registry = new OperationHandlerRegistry(List.of(new OperationPreHandlers(TYPE, List.of(first, second))));

        assertThat(registry.<ProbeContext>preHandlersFor(TYPE)).containsExactly(first, second);
        assertThat(registry.listPreHandlersFor(TYPE)).containsExactly(first, second);
    }

    @Test
    void aHandlerThatMustRunFirstIsAcceptedOnlyAtTheHeadOfTheChain() {
        PreProcessingHandler<ProbeContext> leading = new PreProcessingHandler<>() {
            @Override
            public boolean mustRunFirst() {
                return true;
            }

            @Override
            public PreProcessingResult handleBeforeProcessing(BillingOperationType type, ProbeContext context, PriorAuthorization prior) {
                return PreProcessingResult.proceed();
            }
        };
        PreProcessingHandler<ProbeContext> other = (type, context, prior) -> PreProcessingResult.proceed();

        assertThat(new OperationHandlerRegistry(List.of(new OperationPreHandlers(TYPE, List.of(leading, other)))).listPreHandlersFor(TYPE)).containsExactly(leading, other);
        assertThatThrownBy(() -> new OperationHandlerRegistry(List.of(new OperationPreHandlers(TYPE, List.of(other, leading)))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(TYPE.name());
    }

    @Test
    void anOperationWithoutDeclaredHandlersHasNone() {
        OperationHandlerRegistry registry = new OperationHandlerRegistry(List.of());

        assertThat(registry.<ProbeContext>preHandlersFor(TYPE)).isEmpty();
        assertThat(registry.listPreHandlersFor(TYPE)).isEmpty();
    }

    @Test
    void declaringHandlersTwiceForTheSameOperationIsRejected() {
        List<OperationPreHandlers> twice = List.of(new OperationPreHandlers(TYPE, List.of()), new OperationPreHandlers(TYPE, List.of()));

        assertThatThrownBy(() -> new OperationHandlerRegistry(twice)).isInstanceOf(IllegalStateException.class).hasMessageContaining(TYPE.name());
    }

    @Test
    void operationsAreFoundByTypeAndListed() {
        RecordingOperation operation = new RecordingOperation(new java.util.ArrayList<>());

        BillingOperations operations = new BillingOperations(List.of(operation));

        assertThat(operations.<ProbeContext, String>operationOf(TYPE)).isSameAs(operation);
        assertThat(operations.listAll()).containsExactly(operation);
    }

    @Test
    void anUnregisteredOperationTypeFailsClearly() {
        BillingOperations operations = new BillingOperations(List.of());

        assertThatThrownBy(() -> operations.operationOf(TYPE)).isInstanceOf(IllegalStateException.class).hasMessageContaining(TYPE.name());
    }

    @Test
    void twoOperationsForTheSameTypeAreRejected() {
        List<com.red_escuela.backend.billing.operation.BillingOperation<?, ?>> twice =
                List.of(new RecordingOperation(new java.util.ArrayList<>()), new RecordingOperation(new java.util.ArrayList<>()));

        assertThatThrownBy(() -> new BillingOperations(twice)).isInstanceOf(IllegalStateException.class).hasMessageContaining(TYPE.name());
    }

    @Test
    void preProcessingResultsRejectIncompleteData() {
        assertThatThrownBy(() -> PreProcessingResult.requireAuthorization(null, "r")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PreProcessingResult.requireAuthorization(AuthorizationRole.DIRECTOR, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PreProcessingResult.requireAuthorizationUntil(AuthorizationRole.DIRECTOR, "r", null)).isInstanceOf(NullPointerException.class);
        assertThat(PreProcessingResult.requireAuthorizationUntil(AuthorizationRole.DIRECTOR, "r", LocalDate.of(2026, 1, 1)))
                .isEqualTo(new PreProcessingResult.RequiresAuthorization(AuthorizationRole.DIRECTOR, "r", LocalDate.of(2026, 1, 1)));
        assertThat(PreProcessingResult.proceed()).isSameAs(PreProcessingResult.Proceed.INSTANCE);
    }

    @Test
    void preHandlersRegistrationRequiresATypeAndIsDefensivelyCopied() {
        assertThatThrownBy(() -> new OperationPreHandlers(null, List.of())).isInstanceOf(NullPointerException.class);
        java.util.ArrayList<PreProcessingHandler<?>> mutable = new java.util.ArrayList<>();
        OperationPreHandlers registration = new OperationPreHandlers(TYPE, mutable);
        mutable.add((type, context, prior) -> PreProcessingResult.proceed());

        assertThat(registration.ordered()).isEmpty();
    }

    @Test
    void outcomeReturnsTheResultOrThrowsWhenAwaitingAuthorization() {
        assertThat(new BillingOperationOutcome.Processed<>("x").resultOrThrowIfAwaitingAuthorization(id -> new IllegalStateException())).isEqualTo("x");
        assertThatThrownBy(() -> new BillingOperationOutcome.AwaitingAuthorization<String>(5L)
                .resultOrThrowIfAwaitingAuthorization(id -> new IllegalStateException("pending " + id)))
                .hasMessage("pending 5");
    }

    @Test
    void pendingAuthorizationExceptionCarriesTheRequestId() {
        assertThat(new AuthorizationPendingException(9L).authorizationRequestId()).isEqualTo(9L);
    }

    @Test
    void authorizationRolesAcceptOnlyTheirOwnRoleNames() {
        assertThat(AuthorizationRole.DIRECTOR.isSatisfiedByAnyOf(Set.of("DIRECTOR"))).isTrue();
        assertThat(AuthorizationRole.DIRECTOR.isSatisfiedByAnyOf(Set.of("ADMINISTRATIVO", "DOCENTE"))).isFalse();
        assertThat(AuthorizationRole.DIRECTOR.isSatisfiedByAnyOf(Set.of())).isFalse();
        assertThat(AuthorizationRole.DIRECTOR_OR_ADMINISTRATIVE.isSatisfiedByAnyOf(Set.of("ADMINISTRATIVO"))).isTrue();
        assertThat(AuthorizationRole.DIRECTOR_OR_ADMINISTRATIVE.isSatisfiedByAnyOf(Set.of("DOCENTE"))).isFalse();
        assertThat(AuthorizationRole.DIRECTOR_OR_ADMINISTRATIVE.allowedRoleNames()).containsExactlyInAnyOrder("DIRECTOR", "ADMINISTRATIVO");
    }
}
