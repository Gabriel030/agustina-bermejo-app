package com.red_escuela.backend.billing.events;

import java.util.List;

public record PlannedReminderMessage(PlannedReminderFormat format, PlannedReminderNoticeType leadNoticeType, int priority, boolean precedesDueDate,
                                     List<PlannedReminderBlock> blocks) {

    public PlannedReminderMessage {
        blocks = List.copyOf(blocks);
    }
}
