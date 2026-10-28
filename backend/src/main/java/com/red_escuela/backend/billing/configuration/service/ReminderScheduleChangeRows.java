package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderRepetition;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.ChangeRow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.red_escuela.backend.billing.configuration.service.ConfigurationChangeRowFormats.ABSENT;
import static com.red_escuela.backend.billing.configuration.service.ConfigurationChangeRowFormats.CHANGED_VALUE;
import static com.red_escuela.backend.billing.configuration.service.ConfigurationChangeRowFormats.NEW_VALUE;
import static com.red_escuela.backend.billing.configuration.service.ConfigurationChangeRowFormats.REMOVED_VALUE;
import static com.red_escuela.backend.billing.configuration.service.ConfigurationChangeRowFormats.UNCHANGED;

final class ReminderScheduleChangeRows {

    private ReminderScheduleChangeRows() {
    }

    static List<ChangeRow> buildScheduleRows(ResolvedReminderSchedule today, ResolvedReminderSchedule proposed) {
        List<ChangeRow> rows = new ArrayList<>(buildNoticeRows(today.notices(), proposed.notices()));
        ConfigurationValuesChangeRows.buildCollectionReminderSettingsRows(today.settings(), proposed.settings()).stream()
                .filter(row -> !UNCHANGED.equals(row.difference())).forEach(rows::add);
        return List.copyOf(rows);
    }

    private static List<ChangeRow> buildNoticeRows(List<ReminderNotice> today, List<ReminderNotice> proposed) {
        Map<String, ReminderNotice> todayByKey = today.stream().collect(Collectors.toMap(ReminderNotice::noticeKey, notice -> notice, (first, second) -> first, LinkedHashMap::new));
        Map<String, ReminderNotice> proposedByKey = proposed.stream().collect(Collectors.toMap(ReminderNotice::noticeKey, notice -> notice, (first, second) -> first, LinkedHashMap::new));
        Map<String, ReminderNotice> union = new LinkedHashMap<>(todayByKey);
        proposedByKey.forEach(union::putIfAbsent);
        Map<ReminderMessageType, Integer> typeCounts = new LinkedHashMap<>();
        union.values().forEach(notice -> typeCounts.merge(notice.messageType(), 1, Integer::sum));
        Map<ReminderMessageType, Integer> seenByType = new LinkedHashMap<>();
        List<ChangeRow> rows = new ArrayList<>();
        for (Map.Entry<String, ReminderNotice> entry : union.entrySet()) {
            ReminderMessageType type = entry.getValue().messageType();
            int position = seenByType.merge(type, 1, Integer::sum);
            String label = typeCounts.get(type) > 1 ? messageTypeText(type) + " n.º " + position : messageTypeText(type);
            ReminderNotice before = todayByKey.get(entry.getKey());
            ReminderNotice after = proposedByKey.get(entry.getKey());
            String beforeText = before == null ? ABSENT : noticeText(before);
            String afterText = after == null ? ABSENT : noticeText(after);
            if (before == null || after == null || !beforeText.equals(afterText)) {
                rows.add(new ChangeRow(label, beforeText, afterText, before == null ? NEW_VALUE : after == null ? REMOVED_VALUE : CHANGED_VALUE));
            }
        }
        return rows;
    }

    static String noticeText(ReminderNotice notice) {
        StringBuilder text = new StringBuilder(timingText(notice.offsetDays(), notice.milestone()));
        text.append(" · ").append(channelsText(notice));
        ReminderRepetition repetition = notice.repetition();
        if (repetition != null && repetition.everyDays() > 0) {
            text.append(" · se repite cada ").append(repetition.everyDays() == 1 ? "día" : repetition.everyDays() + " días").append(" hasta ").append(milestoneText(repetition.until()));
        }
        return text.toString();
    }

    private static String timingText(int offsetDays, ReminderMilestone milestone) {
        if (offsetDays == 0) {
            return "El día " + milestoneWithPreposition(milestone);
        }
        int days = Math.abs(offsetDays);
        return days + (days == 1 ? " día " : " días ") + (offsetDays < 0 ? "antes " : "después ") + milestoneWithPreposition(milestone);
    }

    private static String channelsText(ReminderNotice notice) {
        List<String> channels = notice.channels().stream().map(ReminderScheduleChangeRows::channelText).toList();
        return channels.isEmpty() ? "Sin canal" : String.join(" y ", channels);
    }

    private static String channelText(ReminderChannel channel) {
        return switch (channel) {
            case IN_APP -> "App";
            case EMAIL -> "Mail";
        };
    }

    private static String milestoneWithPreposition(ReminderMilestone milestone) {
        return switch (milestone) {
            case ISSUANCE -> "de la emisión";
            case DUE_DATE -> "del vencimiento";
            case SUSPENSION -> "de la suspensión";
        };
    }

    private static String milestoneText(ReminderMilestone milestone) {
        return switch (milestone) {
            case ISSUANCE -> "la emisión";
            case DUE_DATE -> "el vencimiento";
            case SUSPENSION -> "la suspensión";
        };
    }

    private static String messageTypeText(ReminderMessageType type) {
        return switch (type) {
            case ISSUANCE_NOTICE -> "Aviso de emisión";
            case AFTER_ISSUE_REMINDER -> "Recordatorio posterior a la emisión";
            case BEFORE_DUE_REMINDER -> "Recordatorio previo al vencimiento";
            case AFTER_DUE_REMINDER -> "Recordatorio posterior al vencimiento";
            case BEFORE_SUSPENSION_REMINDER -> "Aviso previo a la suspensión";
        };
    }
}
