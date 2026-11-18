package com.red_escuela.backend.enrollment.service;

public record WaitlistEntryContext(WaitlistQueue queue, WaitlistEntryOwner owner, WaitlistCandidate candidate, WaitlistQueueSnapshot snapshot) {
}
