package com.red_escuela.backend.billing.account.policy;

public record AllocationReversalContext(Long allocationId, boolean alreadyReversed, Long lockedAccountId, Long allocationAccountId) {
}
