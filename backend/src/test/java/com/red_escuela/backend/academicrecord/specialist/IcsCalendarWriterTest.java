package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.academicrecord.dto.CalendarEvent;
import com.red_escuela.backend.academicrecord.dto.CalendarEventStatus;
import com.red_escuela.backend.academicrecord.dto.CalendarEventType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IcsCalendarWriterTest {

    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");
    private final IcsCalendarWriter writer = new IcsCalendarWriter();

    private static CalendarEvent classEvent(String title, CalendarEventStatus status) {
        return new CalendarEvent("class-COURSE-7-2026-11-23-18:00", CalendarEventType.CLASS, LocalDate.of(2026, 11, 23),
                LocalTime.of(18, 0), LocalTime.of(20, 0), title, "Anatomía", "Comisión A", "Aula 3", status, null, null);
    }

    private String render(CalendarEvent... events) {
        return new String(writer.write(List.of(events), "escuela.example.com", NOW), StandardCharsets.UTF_8);
    }

    @Test
    void escapesCommaSemicolonBackslashAndNewline() {
        assertThat(IcsCalendarWriter.escapeText("a,b;c\\d\ne\r\nf\rg")).isEqualTo("a\\,b\\;c\\\\d\\ne\\nf\\ng");
    }

    @Test
    void writtenSummaryCarriesEscapedText() {
        String ics = render(classEvent("Clase, parte 1; \\ final", CalendarEventStatus.SCHEDULED));

        assertThat(ics).contains("SUMMARY:Clase\\, parte 1\\; \\\\ final\r\n");
    }

    @Test
    void textInjectingLineBreaksCannotCreateNewProperties() {
        String ics = render(classEvent("Hola\r\nATTENDEE:mailto:x@y.z", CalendarEventStatus.SCHEDULED));

        assertThat(ics).doesNotContain("\r\nATTENDEE");
        assertThat(ics).contains("SUMMARY:Hola\\nATTENDEE:mailto:x@y.z\r\n");
    }

    @Test
    void everyLineIsFoldedToAtMost75OctetsAndEndsWithCrlf() {
        String longTitle = "Ñandú ".repeat(60);

        String ics = render(classEvent(longTitle, CalendarEventStatus.SCHEDULED));

        assertThat(ics).endsWith("\r\n");
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n").doesNotContain("\r");
        for (String line : ics.split("\r\n")) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        String unfolded = ics.replace("\r\n ", "");
        assertThat(unfolded).contains("SUMMARY:" + longTitle);
    }

    @Test
    void foldingNeverSplitsAMultiByteCharacter() {
        StringBuilder document = new StringBuilder();

        IcsCalendarWriter.appendFolded(document, "SUMMARY:" + "€".repeat(100));

        assertThat(document.toString().replace("\r\n ", "")).isEqualTo("SUMMARY:" + "€".repeat(100) + "\r\n");
        for (String line : document.toString().split("\r\n")) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
    }

    @Test
    void calendarDeclaresFixedBuenosAiresTimeZoneAndRefreshHints() {
        String ics = render(classEvent("Clase", CalendarEventStatus.SCHEDULED));

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n");
        assertThat(ics).contains("BEGIN:VTIMEZONE\r\nTZID:America/Argentina/Buenos_Aires\r\n");
        assertThat(ics).contains("TZOFFSETFROM:-0300\r\nTZOFFSETTO:-0300\r\n");
        assertThat(ics).contains("PRODID:").contains("X-WR-CALNAME:");
        assertThat(ics).contains("REFRESH-INTERVAL;VALUE=DURATION:PT1H\r\n").contains("X-PUBLISHED-TTL:PT1H\r\n");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");
    }

    @Test
    void timedEventUsesTzidDtstart() {
        String ics = render(classEvent("Clase", CalendarEventStatus.SCHEDULED));

        assertThat(ics).contains("DTSTART;TZID=America/Argentina/Buenos_Aires:20261123T180000\r\n");
        assertThat(ics).contains("DTEND;TZID=America/Argentina/Buenos_Aires:20261123T200000\r\n");
        assertThat(ics).contains("LOCATION:Aula 3\r\n");
    }

    @Test
    void canceledClassIsMarkedCancelledAndScheduledIsConfirmed() {
        assertThat(render(classEvent("Clase", CalendarEventStatus.CANCELED))).contains("STATUS:CANCELLED\r\n");
        assertThat(render(classEvent("Clase", CalendarEventStatus.SCHEDULED))).contains("STATUS:CONFIRMED\r\n");
    }

    @Test
    void holidayIsAllDayEventWithValueDate() {
        CalendarEvent holiday = new CalendarEvent("holiday-2026-12-08", CalendarEventType.HOLIDAY, LocalDate.of(2026, 12, 8),
                null, null, "Inmaculada Concepción", null, null, null, CalendarEventStatus.SCHEDULED, null, null);

        String ics = render(holiday);

        assertThat(ics).contains("DTSTART;VALUE=DATE:20261208\r\n").contains("DTEND;VALUE=DATE:20261209\r\n");
        assertThat(ics).doesNotContain("TZID=America/Argentina/Buenos_Aires:2026");
    }

    @Test
    void uidIsStableAcrossRenderings() {
        CalendarEvent event = classEvent("Clase", CalendarEventStatus.SCHEDULED);

        String first = new String(writer.write(List.of(event), "escuela.example.com", NOW), StandardCharsets.UTF_8);
        String second = new String(writer.write(List.of(event), "escuela.example.com", NOW.plusSeconds(3600)), StandardCharsets.UTF_8);

        assertThat(first).contains("UID:class-COURSE-7-2026-11-23-18:00@escuela.example.com\r\n");
        assertThat(second).contains("UID:class-COURSE-7-2026-11-23-18:00@escuela.example.com\r\n");
    }

    @Test
    void descriptionHasNoPersonalData() {
        String ics = render(classEvent("Clase", CalendarEventStatus.SCHEDULED));

        assertThat(ics).contains("DESCRIPTION:Anatomía\\nComisión A\r\n");
        assertThat(ics).doesNotContain("ATTENDEE").doesNotContain("ORGANIZER");
    }
}
