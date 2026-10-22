package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ReminderDigestAssembler {

    private ReminderDigestAssembler() {
    }

    public static DailyReminderDigest assembleDigestOf(Long studentId, LocalDate businessDate, List<ReminderDebtEntry> debtInImputationOrder,
                                                       List<ReminderDueNotice> inAppNotices, List<ReminderDueNotice> emailNotices,
                                                       Map<Currency, NonNegativeMoney> creditByCurrency, ReminderDigestMode mode, List<SuppressedReminder> suppressed) {
        Set<Long> debtInvoiceIds = debtInImputationOrder.stream().map(ReminderDebtEntry::invoiceId).collect(Collectors.toSet());
        requireNoticesOfListedDebt(debtInvoiceIds, inAppNotices);
        requireNoticesOfListedDebt(debtInvoiceIds, emailNotices);
        Composer composer = new Composer(businessDate, creditByCurrency);
        List<ReminderMessage> inApp = mode == ReminderDigestMode.DAILY_DIGEST
                ? composer.dailyDigest(debtInImputationOrder, inAppNotices)
                : composer.separateMessages(debtInImputationOrder, inAppNotices);
        List<ReminderMessage> email = mode == ReminderDigestMode.DAILY_DIGEST
                ? composer.dailyDigestWithSuspensionWarningsApart(debtInImputationOrder, emailNotices)
                : composer.separateMessages(debtInImputationOrder, emailNotices);
        return new DailyReminderDigest(studentId, businessDate, inApp, email, suppressed);
    }

    private static void requireNoticesOfListedDebt(Set<Long> debtInvoiceIds, List<ReminderDueNotice> notices) {
        for (ReminderDueNotice notice : notices) {
            if (!debtInvoiceIds.contains(notice.invoiceId())) {
                throw new IllegalArgumentException("The notice " + notice.noticeKey() + " belongs to the invoice " + notice.invoiceId() + ", which is not in the listed debt");
            }
        }
    }

    private record Composer(LocalDate businessDate, Map<Currency, NonNegativeMoney> creditByCurrency) {

        List<ReminderMessage> dailyDigest(List<ReminderDebtEntry> debt, List<ReminderDueNotice> notices) {
            return composeOver(debt, debt, notices).stream().toList();
        }

        List<ReminderMessage> dailyDigestWithSuspensionWarningsApart(List<ReminderDebtEntry> debt, List<ReminderDueNotice> notices) {
            List<ReminderDueNotice> exempt = notices.stream().filter(ReminderDueNotice::isExemptFromTheDailyDigest).toList();
            List<ReminderDueNotice> regular = notices.stream().filter(notice -> !notice.isExemptFromTheDailyDigest()).toList();
            Set<Long> exemptInvoiceIds = exempt.stream().map(ReminderDueNotice::invoiceId).collect(Collectors.toSet());
            List<ReminderDebtEntry> exemptDebt = debt.stream().filter(entry -> exemptInvoiceIds.contains(entry.invoiceId())).toList();
            return Stream.concat(composeOver(debt, debt, regular).stream(), composeOver(debt, exemptDebt, exempt).stream()).toList();
        }

        List<ReminderMessage> separateMessages(List<ReminderDebtEntry> debt, List<ReminderDueNotice> notices) {
            return notices.stream()
                    .flatMap(notice -> composeOver(debt, debt.stream().filter(entry -> entry.invoiceId().equals(notice.invoiceId())).toList(), List.of(notice)).stream())
                    .toList();
        }

        private Optional<ReminderMessage> composeOver(List<ReminderDebtEntry> fullDebt, List<ReminderDebtEntry> debt, List<ReminderDueNotice> notices) {
            List<ReminderDigestLine> imputationOrderDebt = fullDebt.stream().map(entry -> ReminderDigestLine.of(entry, businessDate, List.of())).toList();
            List<ReminderDigestLine> lines = debt.stream()
                    .map(entry -> ReminderDigestLine.of(entry, businessDate, notices.stream()
                            .filter(notice -> notice.invoiceId().equals(entry.invoiceId())).map(ReminderDueNotice::asDigestNotice).toList()))
                    .toList();
            return ReminderMessage.compose(lines, imputationOrderDebt, creditByCurrency);
        }
    }
}
