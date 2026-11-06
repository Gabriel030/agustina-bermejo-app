package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.exception.InvalidJustificationInputException;
import com.red_escuela.backend.attendance.policy.JustificationDeadlinePolicy;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Service;

@Service
public class AttendanceJustificationRecorder {

    private static final int REASON_MAX_LENGTH = 500;

    private final AttendanceActorResolver actorResolver;
    private final JustificationDeadlinePolicy deadlinePolicy;

    public AttendanceJustificationRecorder(AttendanceActorResolver actorResolver, JustificationDeadlinePolicy deadlinePolicy) {
        this.actorResolver = actorResolver;
        this.deadlinePolicy = deadlinePolicy;
    }

    public void justifyByStaff(Attendance attendance, String reason, String attachmentUrl, String exceptionReason) {
        String effectiveReason = reasonWithException(attendance, reason, exceptionReason);
        markJustifiedByCurrentActor(attendance);
        attendance.setJustificationReason(effectiveReason);
        attendance.setJustificationAttachmentUrl(attachmentUrl);
    }

    public void markJustifiedByCurrentActor(Attendance attendance) {
        attendance.setStatus(AttendanceStatus.JUSTIFIED);
        attendance.setJustifiedBy(actorResolver.currentActor());
        attendance.setJustifiedAt(ApplicationTime.now());
    }

    private String reasonWithException(Attendance attendance, String reason, String exceptionReason) {
        if (deadlinePolicy.isWithinDeadline(attendance.getClassDate())) {
            return reason;
        }
        if (exceptionReason == null || exceptionReason.isBlank()) {
            throw new InvalidJustificationInputException("Fuera de plazo hay que indicar el motivo de la excepción");
        }
        String combined = reason + " [Excepción fuera de plazo: " + exceptionReason + "]";
        return combined.length() > REASON_MAX_LENGTH ? combined.substring(0, REASON_MAX_LENGTH) : combined;
    }
}
