package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderRepetition;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

final class ReminderNoticeRules {
    private static final int MAX_NOTICES = 20;
    private static final int MAX_OFFSET_DAYS = 60;
    private static final int MIN_REPETITION_DAYS = 1;
    private static final int MAX_REPETITION_DAYS = 60;

    private ReminderNoticeRules() {
    }

    static String sizeViolation(List<ReminderNotice> notices) {
        return notices.size() > MAX_NOTICES ? "notices: la lista admite como máximo " + MAX_NOTICES + " avisos." : null;
    }

    static String timingViolation(List<ReminderNotice> notices) {
        return firstViolationPerNotice(notices, (notice, path) -> {
            if (Math.abs(notice.offsetDays()) > MAX_OFFSET_DAYS) {
                return path + ".offsetDays: debe estar entre -60 y 60.";
            }
            if (notice.milestone() == ReminderMilestone.ISSUANCE && notice.offsetDays() < 0) {
                return path + ".offsetDays: un aviso desde la emisión no puede ser anterior a ella.";
            }
            if (notice.milestone() == ReminderMilestone.SUSPENSION && notice.offsetDays() > 0) {
                return path + ".offsetDays: un aviso desde la suspensión no puede ser posterior a ella.";
            }
            return null;
        });
    }

    static String channelsViolation(List<ReminderNotice> notices) {
        return firstViolationPerNotice(notices, (notice, path) -> {
            if (notice.channels().isEmpty()) {
                return path + ".channels: debe tener al menos un canal.";
            }
            if (notice.milestone() == ReminderMilestone.ISSUANCE && notice.offsetDays() == 0 && notice.sendsEmail()) {
                return path + ".channels: el día de emisión el aviso va solo por la app.";
            }
            return null;
        });
    }

    static String issuanceNoticeViolation(List<ReminderNotice> notices) {
        int issuanceNotices = 0;
        for (int index = 0; index < notices.size(); index++) {
            ReminderNotice notice = notices.get(index);
            if (notice.messageType() != ReminderMessageType.ISSUANCE_NOTICE) {
                continue;
            }
            String path = "notices[" + index + "]";
            if (!isFixedIssuanceNotice(notice)) {
                return path + ".messageType: el aviso de emisión es el día de emisión, solo por la app y sin repetición.";
            }
            if (++issuanceNotices > 1) {
                return path + ".messageType: solo puede haber un aviso de emisión.";
            }
        }
        return null;
    }

    static String repetitionViolation(List<ReminderNotice> notices) {
        return firstViolationPerNotice(notices, (notice, path) ->
                notice.repetition() == null ? null : repetitionViolation(notice, path + ".repetition"));
    }

    static String uniquenessViolation(List<ReminderNotice> notices) {
        Set<String> keys = new HashSet<>();
        Set<String> slots = new HashSet<>();
        for (int index = 0; index < notices.size(); index++) {
            ReminderNotice notice = notices.get(index);
            String path = "notices[" + index + "]";
            if (!keys.add(notice.noticeKey())) {
                return path + ".noticeKey: está repetida.";
            }
            if (!slots.add(notice.milestone() + ":" + notice.offsetDays())) {
                return path + ".offsetDays: ya hay un aviso en el mismo hito y día.";
            }
        }
        return null;
    }

    private static String firstViolationPerNotice(List<ReminderNotice> notices, BiFunction<ReminderNotice, String, String> rule) {
        for (int index = 0; index < notices.size(); index++) {
            String violation = rule.apply(notices.get(index), "notices[" + index + "]");
            if (violation != null) {
                return violation;
            }
        }
        return null;
    }

    private static boolean isFixedIssuanceNotice(ReminderNotice notice) {
        return notice.milestone() == ReminderMilestone.ISSUANCE && notice.offsetDays() == 0 && notice.repetition() == null
                && notice.channels().equals(Set.of(ReminderChannel.IN_APP));
    }

    private static String repetitionViolation(ReminderNotice notice, String path) {
        ReminderRepetition repetition = notice.repetition();
        if (repetition.everyDays() < MIN_REPETITION_DAYS || repetition.everyDays() > MAX_REPETITION_DAYS) {
            return path + ".everyDays: debe estar entre 1 y 60.";
        }
        if (repetition.until() == null) {
            return path + ".until: es obligatorio.";
        }
        boolean validUntil = switch (repetition.until()) {
            case ISSUANCE -> false;
            case DUE_DATE -> notice.milestone() == ReminderMilestone.ISSUANCE || (notice.milestone() == ReminderMilestone.DUE_DATE && notice.offsetDays() < 0);
            case SUSPENSION -> notice.milestone() != ReminderMilestone.SUSPENSION;
        };
        return validUntil ? null : path + ".until: debe ser un hito posterior al del aviso.";
    }
}
