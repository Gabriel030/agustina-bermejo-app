package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.shared.event.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class RecordingOperationFixtures {

    private RecordingOperationFixtures() {
    }

    record ProbeContext(String value) {
    }

    record ProbeEvent(String correlationId, Instant occurredAt) implements DomainEvent {
    }

    static class RejectedContextException extends RuntimeException {
    }

    static class RecordingOperation implements BillingOperation<ProbeContext, String> {
        final List<String> calls;
        boolean rejectContext;
        int events = 1;

        RecordingOperation(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public BillingOperationType type() {
            return BillingOperationType.CREATE_LATE_PAYMENT_SETTINGS;
        }

        @Override
        public Class<ProbeContext> contextType() {
            return ProbeContext.class;
        }

        @Override
        public List<String> validationMoments() {
            return List.of("PROBE");
        }

        @Override
        public List<Class<? extends DomainEvent>> publishedEventTypes() {
            return List.of(ProbeEvent.class);
        }

        @Override
        public void requireValidContext(ProbeContext context) {
            calls.add("validate");
            if (rejectContext) {
                throw new RejectedContextException();
            }
        }

        @Override
        public String processOperation(ProbeContext context) {
            calls.add("process");
            return "processed-" + context.value();
        }

        @Override
        public List<ProbeEvent> domainEventsOf(ProbeContext context, String result) {
            calls.add("events");
            List<ProbeEvent> produced = new ArrayList<>();
            for (int i = 0; i < events; i++) {
                produced.add(new ProbeEvent("c" + i, Instant.EPOCH));
            }
            return produced;
        }
    }

    static PreProcessingHandler<ProbeContext> handler(List<String> calls, String name, PreProcessingResult result, List<PriorAuthorization> seen) {
        return (type, context, prior) -> {
            calls.add(name);
            seen.add(prior);
            return result;
        };
    }
}
