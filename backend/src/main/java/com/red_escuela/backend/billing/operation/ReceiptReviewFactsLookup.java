package com.red_escuela.backend.billing.operation;

import java.util.Collection;
import java.util.Map;

public interface ReceiptReviewFactsLookup {

    Map<Long, ReceiptReviewFacts> findFactsByReceiptId(Collection<Long> receiptIds);
}
