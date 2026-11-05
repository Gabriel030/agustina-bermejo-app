package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderRepetition;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleSource;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.ChangeRow;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-216")
@Tag("D-351")
class ReminderScheduleChangeRowsTest {

    private static final CollectionReminderSettings SETTINGS = new CollectionReminderSettings(true, 6, LocalTime.of(9, 0), LocalTime.of(11, 0), 250, 14, 2, true);

    private static ReminderNotice notice(String key, ReminderMessageType type, ReminderMilestone milestone, int offset, Set<ReminderChannel> channels, ReminderRepetition repetition) {
        return new ReminderNotice(key, type, milestone, offset, channels, repetition);
    }

    private static ResolvedReminderSchedule resolved(CollectionReminderSettings settings, ReminderNotice... notices) {
        return new ResolvedReminderSchedule(ReminderScheduleSource.GROUP, 1L, null, 1L, null, List.of(notices), settings);
    }

    private static final ReminderNotice BEFORE_DUE = notice("before-due", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -1, EnumSet.allOf(ReminderChannel.class), null);
    private static final ReminderNotice AFTER_DUE = notice("after-due", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 10, EnumSet.of(ReminderChannel.EMAIL), null);

    @Test
    void anUntouchedScheduleProducesNoRows() {
        assertThat(ReminderScheduleChangeRows.buildScheduleRows(resolved(SETTINGS, BEFORE_DUE, AFTER_DUE), resolved(SETTINGS, BEFORE_DUE, AFTER_DUE))).isEmpty();
    }

    @Test
    void aMovedNoticeShowsItsTimingAndChannelsInSpanishBeforeAndAfter() {
        ReminderNotice moved = notice("before-due", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -3, EnumSet.of(ReminderChannel.IN_APP), null);

        List<ChangeRow> rows = ReminderScheduleChangeRows.buildScheduleRows(resolved(SETTINGS, BEFORE_DUE, AFTER_DUE), resolved(SETTINGS, moved, AFTER_DUE));

        assertThat(rows).containsExactly(new ChangeRow("Recordatorio previo al vencimiento", "1 día antes del vencimiento · App y Mail", "3 días antes del vencimiento · App", "Cambia"));
    }

    @Test
    void anAddedNoticeIsNewAndARemovedOneIsDroppedWithRepetitionDescribed() {
        ReminderNotice repeating = notice("before-suspension", ReminderMessageType.BEFORE_SUSPENSION_REMINDER, ReminderMilestone.SUSPENSION, 0, EnumSet.of(ReminderChannel.EMAIL),
                new ReminderRepetition(3, ReminderMilestone.SUSPENSION));

        List<ChangeRow> added = ReminderScheduleChangeRows.buildScheduleRows(resolved(SETTINGS, BEFORE_DUE), resolved(SETTINGS, BEFORE_DUE, repeating));
        List<ChangeRow> removed = ReminderScheduleChangeRows.buildScheduleRows(resolved(SETTINGS, BEFORE_DUE, AFTER_DUE), resolved(SETTINGS, BEFORE_DUE));

        assertThat(added).containsExactly(new ChangeRow("Aviso previo a la suspensión", "—", "El día de la suspensión · Mail · se repite cada 3 días hasta la suspensión", "Nuevo"));
        assertThat(removed).containsExactly(new ChangeRow("Recordatorio posterior al vencimiento", "10 días después del vencimiento · Mail", "—", "Se quita"));
    }

    @Test
    void twoNoticesOfTheSameTypeAreNumberedSoTheRowsCanBeTold() {
        ReminderNotice first = notice("a", ReminderMessageType.AFTER_ISSUE_REMINDER, ReminderMilestone.ISSUANCE, 2, EnumSet.of(ReminderChannel.IN_APP), null);
        ReminderNotice second = notice("b", ReminderMessageType.AFTER_ISSUE_REMINDER, ReminderMilestone.ISSUANCE, 5, EnumSet.of(ReminderChannel.IN_APP), null);
        ReminderNotice secondMoved = notice("b", ReminderMessageType.AFTER_ISSUE_REMINDER, ReminderMilestone.ISSUANCE, 6, EnumSet.of(ReminderChannel.IN_APP), new ReminderRepetition(1, ReminderMilestone.DUE_DATE));

        List<ChangeRow> rows = ReminderScheduleChangeRows.buildScheduleRows(resolved(SETTINGS, first, second), resolved(SETTINGS, first, secondMoved));

        assertThat(rows).containsExactly(new ChangeRow("Recordatorio posterior a la emisión n.º 2", "5 días después de la emisión · App",
                "6 días después de la emisión · App · se repite cada día hasta el vencimiento", "Cambia"));
    }

    @Test
    void onlyTheSendingSettingsThatDifferAreShown() {
        CollectionReminderSettings changed = new CollectionReminderSettings(true, 6, LocalTime.of(9, 0), LocalTime.of(12, 0), 280, 14, 2, true);

        List<ChangeRow> rows = ReminderScheduleChangeRows.buildScheduleRows(resolved(SETTINGS, BEFORE_DUE), resolved(changed, BEFORE_DUE));

        assertThat(rows).containsExactly(new ChangeRow("Envío de mails hasta", "11:00", "12:00", "Cambia"), new ChangeRow("Tope diario de mails", "250 mails", "280 mails", "+30 mails"));
    }
}
