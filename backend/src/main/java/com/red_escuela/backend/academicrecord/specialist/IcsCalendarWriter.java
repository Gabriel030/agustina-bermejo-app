package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.academicrecord.dto.CalendarEvent;
import com.red_escuela.backend.academicrecord.dto.CalendarEventStatus;
import com.red_escuela.backend.academicrecord.dto.CalendarEventType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class IcsCalendarWriter {

    private static final String LINE_BREAK = "\r\n";
    private static final String TIME_ZONE_ID = "America/Argentina/Buenos_Aires";
    private static final String CALENDAR_NAME = "Calendario académico";
    private static final int MAX_LINE_OCTETS = 75;
    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    public byte[] write(List<CalendarEvent> events, String host, Instant generatedAt) {
        List<String> lines = new ArrayList<>(List.of(
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//Instituto//Calendario del alumno//ES",
                "CALSCALE:GREGORIAN",
                "METHOD:PUBLISH",
                "X-WR-CALNAME:" + escapeText(CALENDAR_NAME),
                "X-WR-TIMEZONE:" + TIME_ZONE_ID,
                "REFRESH-INTERVAL;VALUE=DURATION:PT1H",
                "X-PUBLISHED-TTL:PT1H",
                "BEGIN:VTIMEZONE",
                "TZID:" + TIME_ZONE_ID,
                "BEGIN:STANDARD",
                "DTSTART:19700101T000000",
                "TZOFFSETFROM:-0300",
                "TZOFFSETTO:-0300",
                "TZNAME:-03",
                "END:STANDARD",
                "END:VTIMEZONE"));
        String stamp = UTC_DATE_TIME.format(generatedAt.atOffset(ZoneOffset.UTC));
        events.forEach(event -> appendEvent(lines, event, host, stamp));
        lines.add("END:VCALENDAR");
        StringBuilder document = new StringBuilder();
        lines.forEach(line -> appendFolded(document, line));
        return document.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendEvent(List<String> lines, CalendarEvent event, String host, String stamp) {
        lines.add("BEGIN:VEVENT");
        lines.add("UID:" + uidOf(event.id()) + "@" + host);
        lines.add("DTSTAMP:" + stamp);
        lines.add("SUMMARY:" + escapeText(event.title()));
        appendSchedule(lines, event);
        if (hasText(event.place())) {
            lines.add("LOCATION:" + escapeText(event.place()));
        }
        String description = descriptionOf(event);
        if (!description.isEmpty()) {
            lines.add("DESCRIPTION:" + escapeText(description));
        }
        lines.add("STATUS:" + (event.status() == CalendarEventStatus.CANCELED ? "CANCELLED" : "CONFIRMED"));
        lines.add("END:VEVENT");
    }

    private static void appendSchedule(List<String> lines, CalendarEvent event) {
        boolean allDay = event.type() == CalendarEventType.HOLIDAY || event.startTime() == null;
        if (allDay) {
            lines.add("DTSTART;VALUE=DATE:" + DATE.format(event.date()));
            lines.add("DTEND;VALUE=DATE:" + DATE.format(event.date().plusDays(1)));
            return;
        }
        lines.add("DTSTART;TZID=" + TIME_ZONE_ID + ":" + LOCAL_DATE_TIME.format(event.date().atTime(event.startTime())));
        if (event.endTime() != null && event.endTime().isAfter(event.startTime())) {
            lines.add("DTEND;TZID=" + TIME_ZONE_ID + ":" + LOCAL_DATE_TIME.format(event.date().atTime(event.endTime())));
        }
    }

    private static String descriptionOf(CalendarEvent event) {
        List<String> parts = new ArrayList<>();
        if (hasText(event.subjectName())) {
            parts.add(event.subjectName());
        }
        if (hasText(event.sectionName())) {
            parts.add(event.sectionName());
        }
        if (hasText(event.reason())) {
            parts.add(event.reason());
        }
        return String.join("\n", parts);
    }

    private static String uidOf(String eventId) {
        return eventId.replaceAll("[^A-Za-z0-9._~:-]", "-");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static String escapeText(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '\\' -> escaped.append("\\\\");
                case ';' -> escaped.append("\\;");
                case ',' -> escaped.append("\\,");
                case '\n', '\u0085', '\u2028', '\u2029' -> escaped.append("\\n");
                case '\r' -> {
                    if (index + 1 < value.length() && value.charAt(index + 1) == '\n') {
                        index++;
                    }
                    escaped.append("\\n");
                }
                default -> {
                    if (!Character.isISOControl(current)) {
                        escaped.append(current);
                    }
                }
            }
        }
        return escaped.toString();
    }

    static void appendFolded(StringBuilder document, String line) {
        int octetsInLine = 0;
        int index = 0;
        while (index < line.length()) {
            int codePoint = line.codePointAt(index);
            int octets = utf8Length(codePoint);
            if (octetsInLine + octets > MAX_LINE_OCTETS) {
                document.append(LINE_BREAK).append(' ');
                octetsInLine = 1;
            }
            document.appendCodePoint(codePoint);
            octetsInLine += octets;
            index += Character.charCount(codePoint);
        }
        document.append(LINE_BREAK);
    }

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        return codePoint < 0x10000 ? 3 : 4;
    }
}
