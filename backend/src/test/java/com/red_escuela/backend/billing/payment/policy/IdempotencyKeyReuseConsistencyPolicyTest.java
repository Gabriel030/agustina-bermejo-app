package com.red_escuela.backend.billing.payment.policy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-136")
class IdempotencyKeyReuseConsistencyPolicyTest {

    private final IdempotencyKeyReuseConsistencyPolicy policy = new IdempotencyKeyReuseConsistencyPolicy();

    @Test
    void theSameOperationWithTheSameRequestHashIsAllowed() {
        assertThat(policy.evaluateRule(new IdempotencyKeyReuseContext("REGISTER_PAYMENT", "hash-a", "REGISTER_PAYMENT", "hash-a")).allowed()).isTrue();
    }

    @Test
    void anotherRequestHashIsAConflict() {
        var verdict = policy.evaluateRule(new IdempotencyKeyReuseContext("REGISTER_PAYMENT", "hash-a", "REGISTER_PAYMENT", "hash-b"));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(verdict.policy()).isEqualTo("IdempotencyKeyReuseConsistencyPolicy");
    }

    @Test
    void anotherOperationWithTheSameHashIsAConflict() {
        assertThat(policy.evaluateRule(new IdempotencyKeyReuseContext("REGISTER_PAYMENT", "hash-a", "ISSUE_INVOICE", "hash-a")).allowed()).isFalse();
    }

    @Test
    void theRuleNameIsTheClassName() {
        assertThat(policy.ruleName()).isEqualTo("IdempotencyKeyReuseConsistencyPolicy");
    }
}
