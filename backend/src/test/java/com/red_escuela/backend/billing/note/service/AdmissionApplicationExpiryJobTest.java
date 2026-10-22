package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.enrollment.api.AdmissionApplicationExpiryPort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-229")
class AdmissionApplicationExpiryJobTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 4, 2);

    private final AdmissionApplicationExpiryPort repository = mock(AdmissionApplicationExpiryPort.class);
    private final AdmissionApplicationExpiration expiration = mock(AdmissionApplicationExpiration.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-04-02T03:20:00Z"), ZoneId.of("UTC"));
    private final AdmissionApplicationExpiryJob job = new AdmissionApplicationExpiryJob(repository, expiration, clock);

    @Test
    void theJobAsksForTheApplicationsWhoseAdmissionClosedBeforeTodayAndExpiresEachOne() {
        when(repository.findApprovedApplicationIdsWithAdmissionClosedBefore(TODAY)).thenReturn(List.of(1L, 2L));

        job.expireApplicationsWhoseAdmissionClosedUnpaid();

        verify(expiration).expireWhenAdmissionClosedAndUnpaid(1L);
        verify(expiration).expireWhenAdmissionClosedAndUnpaid(2L);
    }

    @Test
    void aFailureOnOneApplicationDoesNotStopTheOthers() {
        when(repository.findApprovedApplicationIdsWithAdmissionClosedBefore(TODAY)).thenReturn(List.of(1L, 2L, 3L));
        when(expiration.expireWhenAdmissionClosedAndUnpaid(2L)).thenThrow(new IllegalStateException("boom"));

        job.expireApplicationsWhoseAdmissionClosedUnpaid();

        verify(expiration).expireWhenAdmissionClosedAndUnpaid(1L);
        verify(expiration).expireWhenAdmissionClosedAndUnpaid(3L);
    }

    @Test
    void withoutCandidatesNothingIsExpired() {
        when(repository.findApprovedApplicationIdsWithAdmissionClosedBefore(TODAY)).thenReturn(List.of());

        job.expireApplicationsWhoseAdmissionClosedUnpaid();

        org.mockito.Mockito.verify(expiration, org.mockito.Mockito.never()).expireWhenAdmissionClosedAndUnpaid(anyLong());
    }
}
