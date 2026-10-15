package com.red_escuela.backend.billing.account.repository;

import com.red_escuela.backend.billing.account.domain.AllocationSourceShare;
import com.red_escuela.backend.billing.account.domain.PaymentAllocation;
import com.red_escuela.backend.billing.account.domain.PaymentAllocationSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Objects;

public class PaymentAllocationSourceRecordingImpl implements PaymentAllocationSourceRecording {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public PaymentAllocation saveWithSources(PaymentAllocation allocation, List<AllocationSourceShare> shares) {
        Objects.requireNonNull(shares, "shares");
        if (shares.isEmpty()) {
            throw new IllegalArgumentException("An allocation needs at least one source");
        }
        entityManager.persist(allocation);
        shares.forEach(share -> entityManager.persist(PaymentAllocationSource.recordSourceOf(allocation, share)));
        return allocation;
    }

    @Override
    public PaymentAllocation saveReversalWithSourcesOfTheReversedAllocation(PaymentAllocation reversal) {
        if (!reversal.isReversal()) {
            throw new IllegalArgumentException("Only a reversal records the reversed sources of another allocation");
        }
        List<PaymentAllocationSource> reversed = entityManager.createQuery(
                "select s from PaymentAllocationSource s where s.paymentAllocationId = :allocationId order by s.id", PaymentAllocationSource.class)
                .setParameter("allocationId", reversal.getReversesAllocationId()).getResultList();
        if (reversed.isEmpty()) {
            throw new IllegalStateException("Allocation " + reversal.getReversesAllocationId() + " has no sources to reverse");
        }
        entityManager.persist(reversal);
        reversed.forEach(source -> entityManager.persist(source.recordReversalOf(reversal)));
        return reversal;
    }
}
