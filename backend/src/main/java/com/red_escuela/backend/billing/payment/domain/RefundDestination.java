package com.red_escuela.backend.billing.payment.domain;

public record RefundDestination(String account, String holder) {

    public static final int MAX_ACCOUNT_LENGTH = BankTransferReceiptLimits.MAX_ORIGIN_ACCOUNT_LENGTH;
    public static final int MAX_HOLDER_LENGTH = BankTransferReceiptLimits.MAX_HOLDER_NAME_LENGTH;

    public RefundDestination {
        account = account == null ? null : account.strip();
        holder = holder == null ? null : holder.strip();
    }

    public static RefundDestination none() {
        return new RefundDestination(null, null);
    }

    public boolean isComplete() {
        return account != null && !account.isEmpty() && holder != null && !holder.isEmpty();
    }

    public boolean isEmpty() {
        return (account == null || account.isEmpty()) && (holder == null || holder.isEmpty());
    }

    public boolean exceedsColumnLimits() {
        return (account != null && account.length() > MAX_ACCOUNT_LENGTH) || (holder != null && holder.length() > MAX_HOLDER_LENGTH);
    }
}
