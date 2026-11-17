package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.WaitlistHistoryEntryResponse;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.dto.WaitlistQueueResponse;
import com.red_escuela.backend.enrollment.dto.WaitlistQueueSummaryResponse;
import com.red_escuela.backend.enrollment.usecase.waitlist.GetWaitlistHistoryUseCase;
import com.red_escuela.backend.enrollment.usecase.waitlist.GetWaitlistQueueUseCase;
import com.red_escuela.backend.enrollment.usecase.waitlist.ListWaitlistQueuesUseCase;
import com.red_escuela.backend.enrollment.usecase.waitlist.ListWaitlistQueuesUseCase.ListWaitlistQueuesCommand;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/waitlists")
public class WaitlistController {

    private final ListWaitlistQueuesUseCase listWaitlistQueuesUseCase;
    private final GetWaitlistQueueUseCase getWaitlistQueueUseCase;
    private final GetWaitlistHistoryUseCase getWaitlistHistoryUseCase;

    public WaitlistController(ListWaitlistQueuesUseCase listWaitlistQueuesUseCase,
                              GetWaitlistQueueUseCase getWaitlistQueueUseCase,
                              GetWaitlistHistoryUseCase getWaitlistHistoryUseCase) {
        this.listWaitlistQueuesUseCase = listWaitlistQueuesUseCase;
        this.getWaitlistQueueUseCase = getWaitlistQueueUseCase;
        this.getWaitlistHistoryUseCase = getWaitlistHistoryUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public PageResponse<WaitlistQueueSummaryResponse> listQueues(@RequestParam(required = false) WaitlistKind kind,
                                                                 @RequestParam(required = false) Long termId,
                                                                 @RequestParam(required = false) Integer expiringWithinDays,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return listWaitlistQueuesUseCase.list(new ListWaitlistQueuesCommand(kind, termId, expiringWithinDays, page, size));
    }

    @GetMapping("/{kind}/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public WaitlistQueueResponse getQueue(@PathVariable WaitlistKind kind, @PathVariable Long id) {
        return getWaitlistQueueUseCase.get(kind, id);
    }

    @GetMapping("/{kind}/{id}/history")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public PageResponse<WaitlistHistoryEntryResponse> getHistory(@PathVariable WaitlistKind kind, @PathVariable Long id,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return getWaitlistHistoryUseCase.get(kind, id, page, size);
    }
}
