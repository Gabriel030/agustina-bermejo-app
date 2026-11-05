package com.red_escuela.backend.notification.reporting.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderNoticeTest {

    @ParameterizedTest
    @CsvSource({
            "OWN_NOTICE:ISSUANCE_NOTICE,ISSUANCE_NOTICE",
            "OWN_NOTICE:AFTER_ISSUE_REMINDER,AFTER_ISSUE_REMINDER",
            "DAILY_DIGEST:BEFORE_DUE_REMINDER,BEFORE_DUE_REMINDER",
            "OWN_NOTICE:AFTER_DUE_REMINDER,AFTER_DUE_REMINDER",
            "DAILY_DIGEST:BEFORE_SUSPENSION_REMINDER,BEFORE_SUSPENSION_REMINDER",
            "DEBT_SUMMARY:AFTER_DUE_REMINDER,DEBT_SUMMARY",
            "DEBT_SUMMARY:ISSUANCE_NOTICE,DEBT_SUMMARY",
            "POST_WITHDRAWAL_SUMMARY,POST_WITHDRAWAL_SUMMARY",
            "OWN_NOTICE:UNKNOWN_NOTICE,OTHER",
            "SOMETHING,OTHER"})
    void theNoticeIsTakenFromTheKindOfTheDelivery(String kind, ReminderNotice expected) {
        assertThat(ReminderNotice.ofDeliveryKind(kind)).isEqualTo(expected);
    }

    @Test
    void onlyThePostWithdrawalSummaryAndTheUnknownKindsAreNotAlwaysReported() {
        for (ReminderNotice notice : ReminderNotice.values()) {
            boolean expected = notice != ReminderNotice.POST_WITHDRAWAL_SUMMARY && notice != ReminderNotice.OTHER;
            assertThat(notice.isAlwaysReported()).isEqualTo(expected);
        }
    }
}
