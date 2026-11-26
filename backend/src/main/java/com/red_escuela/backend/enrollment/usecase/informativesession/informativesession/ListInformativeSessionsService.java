package com.red_escuela.backend.enrollment.usecase.informativesession;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.entity.InformativeSessionStatus;
import com.red_escuela.backend.enrollment.service.InformativeSessionReader;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.shared.UseCase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@UseCase
public class ListInformativeSessionsService implements ListInformativeSessionsUseCase {

    private final InformativeSessionReader informativeSessionReader;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public ListInformativeSessionsService(InformativeSessionReader informativeSessionReader,
                                          CurrentActorFinder currentActorFinder, Clock clock) {
        this.informativeSessionReader = informativeSessionReader;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Override
    public List<InformativeSessionResponse> list(InformativeSessionStatus status, LocalDate from) {
        InformativeSessionStatus effectiveStatus = Optional.ofNullable(status).orElse(InformativeSessionStatus.SCHEDULED);
        LocalDateTime effectiveFrom = Optional.ofNullable(from).map(LocalDate::atStartOfDay).orElseGet(() -> LocalDateTime.now(clock));
        return informativeSessionReader.list(effectiveStatus, effectiveFrom, currentActorFinder.currentPersonId());
    }
}
