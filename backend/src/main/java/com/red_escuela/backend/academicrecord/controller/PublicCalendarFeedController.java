package com.red_escuela.backend.academicrecord.controller;

import com.red_escuela.backend.academicrecord.usecase.ReadPublicCalendarFeedUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/public/calendar")
public class PublicCalendarFeedController {

    private static final MediaType TEXT_CALENDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");

    private final ReadPublicCalendarFeedUseCase readPublicFeedUseCase;

    public PublicCalendarFeedController(ReadPublicCalendarFeedUseCase readPublicFeedUseCase) {
        this.readPublicFeedUseCase = readPublicFeedUseCase;
    }

    @GetMapping("/{token}.ics")
    public ResponseEntity<byte[]> calendarFeed(@PathVariable String token, HttpServletRequest request) {
        byte[] document = readPublicFeedUseCase.read(token, request.getRemoteAddr());
        return ResponseEntity.ok()
                .contentType(TEXT_CALENDAR)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(15)).cachePrivate())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex")
                .body(document);
    }
}
