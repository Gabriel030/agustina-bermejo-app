package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Service;

@Service
public class AttendanceJustificationRecorder {

    private final AttendanceActorResolver actorResolver;

    public AttendanceJustificationRecorder(AttendanceActorResolver actorResolver) {
        this.actorResolver = actorResolver;
    }

    public void markJustifiedByCurrentActor(Attendance attendance) {
        attendance.setStatus(AttendanceStatus.JUSTIFIED);
        attendance.setJustifiedBy(actorResolver.currentActor());
        attendance.setJustifiedAt(ApplicationTime.now());
    }
}
