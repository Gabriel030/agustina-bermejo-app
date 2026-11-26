package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.dto.ListInformativeSessionsQuery;
import com.red_escuela.backend.enrollment.entity.InformativeSessionStatus;
import com.red_escuela.backend.enrollment.usecase.informativesession.ListInformativeSessionsUseCase;
import com.red_escuela.backend.enrollment.usecase.informativesession.RegisterForInformativeSessionUseCase;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/informative-sessions")
public class MeInformativeSessionController {

    private final ListInformativeSessionsUseCase listInformativeSessionsUseCase;
    private final RegisterForInformativeSessionUseCase registerForInformativeSessionUseCase;

    public MeInformativeSessionController(ListInformativeSessionsUseCase listInformativeSessionsUseCase,
                                          RegisterForInformativeSessionUseCase registerForInformativeSessionUseCase) {
        this.listInformativeSessionsUseCase = listInformativeSessionsUseCase;
        this.registerForInformativeSessionUseCase = registerForInformativeSessionUseCase;
    }

    @GetMapping
    public List<InformativeSessionResponse> list(
            @RequestParam(required = false) InformativeSessionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        return listInformativeSessionsUseCase.list(new ListInformativeSessionsQuery(status, from));
    }

    @PostMapping("/{id}/register")
    public InformativeSessionResponse register(@PathVariable Long id) {
        return registerForInformativeSessionUseCase.register(id);
    }
}
