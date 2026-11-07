package com.red_escuela.backend.billing.configuration.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.EnumSet;
import java.util.Set;

public record ReminderNoticeDto(@Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String noticeKey, @NotNull ReminderMessageType messageType, @NotNull ReminderMilestone milestone, @NotNull Integer offsetDays,
                                @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Set<ReminderChannel> channels, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Valid ReminderRepetitionDto repetition) {

    public ReminderNotice toNotice() {
        return new ReminderNotice(noticeKey, messageType, milestone, offsetDays, channels, repetition == null ? null : repetition.toRepetition());
    }

    public static ReminderNoticeDto from(ReminderNotice notice) {
        return new ReminderNoticeDto(notice.noticeKey(), notice.messageType(), notice.milestone(), notice.offsetDays(), EnumSet.copyOf(notice.channels()),
                ReminderRepetitionDto.from(notice.repetition()));
    }
}
