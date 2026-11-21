package com.red_escuela.backend.academicrecord.controller;

import com.red_escuela.backend.academicrecord.dto.CalendarFeedStatusResponse;
import com.red_escuela.backend.academicrecord.dto.CalendarFeedTokenResponse;
import com.red_escuela.backend.academicrecord.dto.MyCalendarResponse;
import com.red_escuela.backend.academicrecord.usecase.ExportMyCalendarCommand;
import com.red_escuela.backend.academicrecord.usecase.ExportMyCalendarUseCase;
import com.red_escuela.backend.academicrecord.usecase.ReadMyCalendarFeedStatusUseCase;
import com.red_escuela.backend.academicrecord.usecase.ReadMyCalendarUseCase;
import com.red_escuela.backend.academicrecord.usecase.RevokeMyCalendarFeedTokenUseCase;
import com.red_escuela.backend.academicrecord.usecase.RotateMyCalendarFeedTokenUseCase;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.specialist.CurrentStudentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/me/calendar")
public class MeCalendarFeedController {

    private static final MediaType TEXT_CALENDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");

    private final CurrentStudentService currentStudentService;
    private final ReadMyCalendarFeedStatusUseCase readFeedStatusUseCase;
    private final RotateMyCalendarFeedTokenUseCase rotateFeedTokenUseCase;
    private final RevokeMyCalendarFeedTokenUseCase revokeFeedTokenUseCase;
    private final ExportMyCalendarUseCase exportCalendarUseCase;
    private final ReadMyCalendarUseCase readCalendarUseCase;

    public MeCalendarFeedController(
            CurrentStudentService currentStudentService,
            ReadMyCalendarFeedStatusUseCase readFeedStatusUseCase,
            RotateMyCalendarFeedTokenUseCase rotateFeedTokenUseCase,
            RevokeMyCalendarFeedTokenUseCase revokeFeedTokenUseCase,
            ExportMyCalendarUseCase exportCalendarUseCase,
            ReadMyCalendarUseCase readCalendarUseCase) {
        this.currentStudentService = currentStudentService;
        this.readFeedStatusUseCase = readFeedStatusUseCase;
        this.rotateFeedTokenUseCase = rotateFeedTokenUseCase;
        this.revokeFeedTokenUseCase = revokeFeedTokenUseCase;
        this.exportCalendarUseCase = exportCalendarUseCase;
        this.readCalendarUseCase = readCalendarUseCase;
    }

    @GetMapping
    public ResponseEntity<MyCalendarResponse> myCalendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        MyCalendarResponse response = currentStudentService.findCurrentStudent()
                .map(Student::getId)
                .map(studentId -> readCalendarUseCase.read(studentId, from, to))
                .orElseGet(() -> readCalendarUseCase.readEmpty(from, to));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, max-age=60").body(response);
    }

    @GetMapping("/feed")
    public CalendarFeedStatusResponse myCalendarFeed() {
        return currentStudentService.findCurrentStudent()
                .map(student -> readFeedStatusUseCase.read(student.getId()))
                .orElseGet(CalendarFeedStatusResponse::inactive);
    }

    @PostMapping("/feed/token")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarFeedTokenResponse rotateMyCalendarFeedToken() {
        return rotateFeedTokenUseCase.rotate(currentStudentService.resolveCurrentStudent().getId());
    }

    @DeleteMapping("/feed/token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeMyCalendarFeedToken() {
        currentStudentService.findCurrentStudent().ifPresent(student -> revokeFeedTokenUseCase.revoke(student.getId()));
    }

    @GetMapping("/export.ics")
    public ResponseEntity<byte[]> exportMyCalendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        byte[] document = exportCalendarUseCase.export(new ExportMyCalendarCommand(studentId, from, to));
        return ResponseEntity.ok()
                .contentType(TEXT_CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("calendario.ics").build().toString())
                .body(document);
    }
}
