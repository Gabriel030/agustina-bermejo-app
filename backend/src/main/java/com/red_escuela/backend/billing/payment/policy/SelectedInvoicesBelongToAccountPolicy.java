package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.ReceiptInvoiceFact;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"INVOICE_CURRENCY_MISMATCH", "INVOICE_OF_ANOTHER_ACCOUNT", "INVOICE_NOT_PAYABLE_BY_RECEIPT"}, decisions = {"D-097", "D-153"})
public final class SelectedInvoicesBelongToAccountPolicy implements Policy<BankTransferReceiptRegistrationCheck> {

    @Override
    public String ruleName() {
        return "SelectedInvoicesBelongToAccountPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(BankTransferReceiptRegistrationCheck check) {
        for (ReceiptInvoiceFact invoice : check.selectedInvoices()) {
            if (!invoice.studentCurrentAccountId().equals(check.studentCurrentAccountId())) {
                return PolicyVerdict.deny(ruleName(), "INVOICE_OF_ANOTHER_ACCOUNT", "La factura no pertenece a la cuenta del alumno.");
            }
            if (invoice.currency() != check.declaredCurrency()) {
                return PolicyVerdict.deny(ruleName(), "INVOICE_CURRENCY_MISMATCH", "El monto y las facturas tienen que estar en la misma moneda.");
            }
            if (!invoice.isOpenToReceipt() && !invoice.isUnderReview()) {
                return PolicyVerdict.deny(ruleName(), "INVOICE_NOT_PAYABLE_BY_RECEIPT", "La factura no está emitida o ya no tiene saldo por pagar.");
            }
        }
        return PolicyVerdict.allow(ruleName());
    }
}
