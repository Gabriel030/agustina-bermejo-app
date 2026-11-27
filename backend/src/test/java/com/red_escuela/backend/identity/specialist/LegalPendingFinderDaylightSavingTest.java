package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.LegalDocumentResponse;
import com.red_escuela.backend.identity.dto.PendingLegalAcceptancesResponse;
import com.red_escuela.backend.identity.entity.LegalDocumentPublication;
import com.red_escuela.backend.identity.entity.LegalDocumentType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.LegalDocumentPublicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegalPendingFinderDaylightSavingTest {

    private static final long GRACE_DAYS = 7;
    private static final LegalDocumentResponse TERMS = new LegalDocumentResponse(LegalDocumentType.TERMS, "v2", "/legal/terms");

    private final CurrentPersonService currentPersonService = mock(CurrentPersonService.class);
    private final LegalAcceptanceRecorder legalAcceptanceRecorder = mock(LegalAcceptanceRecorder.class);
    private final LegalDocumentPublicationRepository publicationRepository = mock(LegalDocumentPublicationRepository.class);
    private final LegalMinorChecker legalMinorChecker = mock(LegalMinorChecker.class);

    private PendingLegalAcceptancesResponse pendingAt(String zone, String firstSeenAt, String nowLocalDateTime) {
        ZoneId zoneId = ZoneId.of(zone);
        Clock clock = Clock.fixed(LocalDateTime.parse(nowLocalDateTime).atZone(zoneId).toInstant(), zoneId);
        Person person = mock(Person.class);
        when(person.getId()).thenReturn(1L);
        when(currentPersonService.resolveCurrentPerson()).thenReturn(person);
        when(legalAcceptanceRecorder.pendingDocuments(1L)).thenReturn(List.of(TERMS));
        LegalDocumentPublication publication = new LegalDocumentPublicationStub();
        ReflectionTestUtils.setField(publication, "firstSeenAt", LocalDateTime.parse(firstSeenAt));
        when(publicationRepository.findByDocumentTypeAndVersion(any(), anyString())).thenReturn(Optional.of(publication));
        LegalPendingFinder finder = new LegalPendingFinder(currentPersonService, legalAcceptanceRecorder, publicationRepository,
                legalMinorChecker, clock, GRACE_DAYS);
        return finder.pendingForCurrentPerson();
    }

    @ParameterizedTest
    @CsvSource({
            "America/New_York,2027-03-12T23:30:00",
            "America/New_York,2027-03-13T12:00:00",
            "America/New_York,2027-11-06T23:30:00",
            "Europe/Madrid,2027-03-27T23:30:00",
            "Europe/Madrid,2027-10-30T12:00:00"})
    void deadlineSpanningADaylightSavingTransitionKeepsTheCalendarDayCount(String zone, String firstSeenAt) {
        PendingLegalAcceptancesResponse response = pendingAt(zone, firstSeenAt, firstSeenAt);

        LocalDate firstSeenDay = LocalDateTime.parse(firstSeenAt).toLocalDate();
        assertThat(response.suggestedDeadline()).isEqualTo(firstSeenDay.plusDays(GRACE_DAYS));
        assertThat(ChronoUnit.DAYS.between(firstSeenDay, response.suggestedDeadline())).isEqualTo(GRACE_DAYS);
    }

    @Test
    void graceStaysOpenThroughTheDeadlineDayAndExpiresTheNextOneAcrossTheSpringForward() {
        String firstSeenAt = "2027-03-10T22:00:00";

        PendingLegalAcceptancesResponse onDeadlineDay = pendingAt("America/New_York", firstSeenAt, "2027-03-17T23:59:00");
        PendingLegalAcceptancesResponse dayAfter = pendingAt("America/New_York", firstSeenAt, "2027-03-18T00:30:00");

        assertThat(onDeadlineDay.suggestedDeadline()).isEqualTo(LocalDate.of(2027, 3, 17));
        assertThat(onDeadlineDay.gracePeriodExpired()).isFalse();
        assertThat(dayAfter.gracePeriodExpired()).isTrue();
    }

    private static final class LegalDocumentPublicationStub extends LegalDocumentPublication {
    }
}
