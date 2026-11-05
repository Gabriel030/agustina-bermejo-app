package com.red_escuela.backend.graduation.certificate.regular.service;

import com.red_escuela.backend.attendance.api.AttendanceStandingView;

import java.util.List;

record AttendanceStandingSummary(boolean controlled, boolean anyFree) {

    static AttendanceStandingSummary of(List<AttendanceStandingView> views) {
        return new AttendanceStandingSummary(
                views.stream().anyMatch(AttendanceStandingView::calculable),
                views.stream().anyMatch(AttendanceStandingView::free));
    }
}
