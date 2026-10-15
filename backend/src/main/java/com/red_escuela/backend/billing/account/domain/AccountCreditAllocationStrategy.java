package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.List;

public interface AccountCreditAllocationStrategy {

    List<PlannedInvoiceAllocation> planFullInvoiceAllocations(NonNegativeMoney unallocatedCredit, List<OpenInvoiceView> openInvoices);

    List<OpenInvoiceView> sortInAllocationOrder(List<OpenInvoiceView> openInvoices);
}
