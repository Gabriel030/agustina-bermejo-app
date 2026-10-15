package com.red_escuela.backend.billing.configuration.dto;

import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReminderScheduleDto(Boolean followsGroupSchedule, @NotNull List<@NotNull @Valid ReminderNoticeDto> notices, @Valid ReminderSendingOverridesDto sendingOverrides) {

    public ReminderSchedule toSchedule() {
        List<ReminderNotice> domainNotices = notices.stream().map(ReminderNoticeDto::toNotice).toList();
        return new ReminderSchedule(false, domainNotices, sendingOverrides == null ? null : sendingOverrides.toOverrides());
    }

    public static ReminderScheduleDto from(ReminderSchedule schedule) {
        return new ReminderScheduleDto(schedule.followsGroupSchedule(), schedule.orderedNotices().stream().map(ReminderNoticeDto::from).toList(),
                ReminderSendingOverridesDto.from(schedule.sendingOverrides()));
    }
}
