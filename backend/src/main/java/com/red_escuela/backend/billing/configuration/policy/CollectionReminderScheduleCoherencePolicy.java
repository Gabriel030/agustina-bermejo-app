package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderRepetition;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@PolicyRule(violationCodes = "INVALID_COLLECTION_REMINDER_SCHEDULE", decisions = {"D-368", "D-344"})
public final class CollectionReminderScheduleCoherencePolicy implements Policy<ReminderScheduleCheck> {
    private static final String CODE = "INVALID_COLLECTION_REMINDER_SCHEDULE";
    private static final int MAX_NOTICES = 20;
    private static final int MAX_OFFSET_DAYS = 60;
    private static final int MIN_REPETITION_DAYS = 1;
    private static final int MAX_REPETITION_DAYS = 60;

    public String ruleName() {
        return "CollectionReminderScheduleCoherencePolicy";
    }

    public PolicyVerdict evaluateRule(ReminderScheduleCheck check) {
        if (check.schedule().followsGroupSchedule()) {
            return PolicyVerdict.allow(ruleName());
        }
        String violation = firstViolationOf(check.schedule().notices());
        return violation == null ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), CODE, violation);
    }

    private String firstViolationOf(List<ReminderNotice> notices) {
        if (notices.size() > MAX_NOTICES) {
            return "notices: la lista admite como máximo " + MAX_NOTICES + " avisos.";
        }
        Set<String> keys = new HashSet<>();
        Set<String> slots = new HashSet<>();
        int issuanceNotices = 0;
        for (int index = 0; index < notices.size(); index++) {
            ReminderNotice notice = notices.get(index);
            String path = "notices[" + index + "]";
            String violation = noticeViolation(notice, path);
            if (violation != null) {
                return violation;
            }
            if (!keys.add(notice.noticeKey())) {
                return path + ".noticeKey: está repetida.";
            }
            if (notice.messageType() == ReminderMessageType.ISSUANCE_NOTICE && ++issuanceNotices > 1) {
                return path + ".messageType: solo puede haber un aviso de emisión.";
            }
            if (!slots.add(notice.milestone() + ":" + notice.offsetDays())) {
                return path + ".offsetDays: ya hay un aviso en el mismo hito y día.";
            }
        }
        return null;
    }

    private String noticeViolation(ReminderNotice notice, String path) {
        if (Math.abs(notice.offsetDays()) > MAX_OFFSET_DAYS) {
            return path + ".offsetDays: debe estar entre -60 y 60.";
        }
        if (notice.milestone() == ReminderMilestone.ISSUANCE && notice.offsetDays() < 0) {
            return path + ".offsetDays: un aviso desde la emisión no puede ser anterior a ella.";
        }
        if (notice.milestone() == ReminderMilestone.SUSPENSION && notice.offsetDays() > 0) {
            return path + ".offsetDays: un aviso desde la suspensión no puede ser posterior a ella.";
        }
        if (notice.channels().isEmpty()) {
            return path + ".channels: debe tener al menos un canal.";
        }
        if (notice.milestone() == ReminderMilestone.ISSUANCE && notice.offsetDays() == 0 && notice.sendsEmail()) {
            return path + ".channels: el día de emisión el aviso va solo por la app.";
        }
        if (notice.messageType() == ReminderMessageType.ISSUANCE_NOTICE && !isFixedIssuanceNotice(notice)) {
            return path + ".messageType: el aviso de emisión es el día de emisión, solo por la app y sin repetición.";
        }
        return notice.repetition() == null ? null : repetitionViolation(notice, path + ".repetition");
    }

    private boolean isFixedIssuanceNotice(ReminderNotice notice) {
        return notice.milestone() == ReminderMilestone.ISSUANCE && notice.offsetDays() == 0 && notice.repetition() == null
                && notice.channels().equals(Set.of(ReminderChannel.IN_APP));
    }

    private String repetitionViolation(ReminderNotice notice, String path) {
        ReminderRepetition repetition = notice.repetition();
        if (repetition.everyDays() < MIN_REPETITION_DAYS || repetition.everyDays() > MAX_REPETITION_DAYS) {
            return path + ".everyDays: debe estar entre 1 y 60.";
        }
        boolean validUntil = switch (repetition.until()) {
            case ISSUANCE -> false;
            case DUE_DATE -> notice.milestone() == ReminderMilestone.ISSUANCE || (notice.milestone() == ReminderMilestone.DUE_DATE && notice.offsetDays() < 0);
            case SUSPENSION -> notice.milestone() != ReminderMilestone.SUSPENSION;
        };
        return validUntil ? null : path + ".until: debe ser un hito posterior al del aviso.";
    }
}
