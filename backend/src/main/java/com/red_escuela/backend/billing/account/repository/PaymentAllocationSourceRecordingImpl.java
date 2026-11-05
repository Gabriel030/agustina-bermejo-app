package com.red_escuela.backend.billing.account.repository;

import com.red_escuela.backend.billing.account.domain.AllocationSourceShare;
import com.red_escuela.backend.billing.account.domain.PaymentAllocation;
import com.red_escuela.backend.billing.account.domain.PaymentAllocationSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public Map<Long, NonNegativeMoney> netSourcedByCreditEntry(Long studentCurrentAccountId) {
        Map<Long, Money> net = new HashMap<>();
        entityManager.createQuery("select s from PaymentAllocationSource s where s.studentCurrentAccountId = :accountId", PaymentAllocationSource.class)
                .setParameter("accountId", studentCurrentAccountId).getResultList().forEach(source -> {
                    Money amount = source.getAmount().value();
                    Money signed = source.isReversal() ? Money.zero(amount.currency()).minus(amount) : amount;
                    net.merge(source.getCreditEntryId(), signed, Money::plus);
                });
        Map<Long, NonNegativeMoney> result = new HashMap<>();
        net.forEach((entryId, money) -> result.put(entryId, NonNegativeMoney.of(money)));
        return result;
    }

    @Override
    public List<PaymentAllocation> findCurrentAllocationsFundedByPaymentNewestFirst(Long studentCurrentAccountId, Long paymentId) {
        return entityManager.createQuery("""
                select a from PaymentAllocation a
                where a.studentCurrentAccountId = :accountId and a.reversesAllocationId is null
                  and not exists (select 1 from PaymentAllocation r where r.reversesAllocationId = a.id)
                  and exists (select 1 from PaymentAllocationSource s where s.paymentAllocationId = a.id and s.paymentId = :paymentId)
                order by a.allocatedOn desc, a.id desc
                """, PaymentAllocation.class)
                .setParameter("accountId", studentCurrentAccountId).setParameter("paymentId", paymentId).getResultList();
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
