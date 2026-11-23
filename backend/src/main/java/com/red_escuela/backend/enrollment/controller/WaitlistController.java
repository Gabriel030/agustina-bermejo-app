package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.WaitlistHistoryEntryResponse;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.dto.WaitlistQueueResponse;
import com.red_escuela.backend.enrollment.dto.WaitlistQueueSummaryResponse;
import com.red_escuela.backend.enrollment.dto.WaitlistReasonRequest;
import com.red_escuela.backend.enrollment.usecase.waitlist.GetWaitlistHistoryUseCase;
import com.red_escuela.backend.enrollment.usecase.waitlist.GetWaitlistQueueUseCase;
import com.red_escuela.backend.enrollment.usecase.waitlist.ListWaitlistQueuesUseCase;
import com.red_escuela.backend.enrollment.usecase.waitlist.PrioritizeWaitlistEntryUseCase;
import com.red_escuela.backend.enrollment.usecase.waitlist.PromoteWaitlistEntryUseCase;
import com.red_escuela.backend.enrollment.usecase.waitlist.RemoveWaitlistEntryUseCase;
import com.red_escuela.backend.enrollment.usecase.waitlist.ListWaitlistQueuesUseCase.ListWaitlistQueuesCommand;
import com.red_escuela.backend.shared.pagination.PageResponse;
import jakarta.validation.Valid;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/waitlists")
public class WaitlistController {

    private final ListWaitlistQueuesUseCase listWaitlistQueuesUseCase;
    private final GetWaitlistQueueUseCase getWaitlistQueueUseCase;
    private final GetWaitlistHistoryUseCase getWaitlistHistoryUseCase;
    private final PromoteWaitlistEntryUseCase promoteWaitlistEntryUseCase;
    private final PrioritizeWaitlistEntryUseCase prioritizeWaitlistEntryUseCase;
    private final RemoveWaitlistEntryUseCase removeWaitlistEntryUseCase;

    public WaitlistController(ListWaitlistQueuesUseCase listWaitlistQueuesUseCase,
                              GetWaitlistQueueUseCase getWaitlistQueueUseCase,
                              GetWaitlistHistoryUseCase getWaitlistHistoryUseCase,
                              PromoteWaitlistEntryUseCase promoteWaitlistEntryUseCase,
                              PrioritizeWaitlistEntryUseCase prioritizeWaitlistEntryUseCase,
                              RemoveWaitlistEntryUseCase removeWaitlistEntryUseCase) {
        this.promoteWaitlistEntryUseCase = promoteWaitlistEntryUseCase;
        this.prioritizeWaitlistEntryUseCase = prioritizeWaitlistEntryUseCase;
        this.removeWaitlistEntryUseCase = removeWaitlistEntryUseCase;
        this.listWaitlistQueuesUseCase = listWaitlistQueuesUseCase;
        this.getWaitlistQueueUseCase = getWaitlistQueueUseCase;
        this.getWaitlistHistoryUseCase = getWaitlistHistoryUseCase;
    }

    @GetMapping
    @RequiresPermission(code = "enrollment.read_waitlists", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public PageResponse<WaitlistQueueSummaryResponse> listQueues(@RequestParam(required = false) WaitlistKind kind,
                                                                 @RequestParam(required = false) Long termId,
                                                                 @RequestParam(required = false) Integer expiringWithinDays,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return listWaitlistQueuesUseCase.list(new ListWaitlistQueuesCommand(kind, termId, expiringWithinDays, page, size));
    }

    @GetMapping("/{kind}/{id}")
    @RequiresPermission(code = "enrollment.read_waitlists", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public WaitlistQueueResponse getQueue(@PathVariable WaitlistKind kind, @PathVariable Long id) {
        return getWaitlistQueueUseCase.get(kind, id);
    }

    @GetMapping("/{kind}/{id}/history")
    @RequiresPermission(code = "enrollment.read_waitlists", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public PageResponse<WaitlistHistoryEntryResponse> getHistory(@PathVariable WaitlistKind kind, @PathVariable Long id,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return getWaitlistHistoryUseCase.get(kind, id, page, size);
    }

    @PostMapping("/{kind}/entries/{enrollmentId}/promote")
    @RequiresPermission(code = "enrollment.manage_waitlist", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public void promote(@PathVariable WaitlistKind kind, @PathVariable Long enrollmentId) {
        promoteWaitlistEntryUseCase.promote(kind, enrollmentId);
    }

    @PostMapping("/{kind}/entries/{enrollmentId}/prioritize")
    @RequiresPermission(code = "enrollment.manage_waitlist", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public void prioritize(@PathVariable WaitlistKind kind, @PathVariable Long enrollmentId, @Valid @RequestBody WaitlistReasonRequest request) {
        prioritizeWaitlistEntryUseCase.prioritize(kind, enrollmentId, request.reason());
    }

    @PostMapping("/{kind}/entries/{enrollmentId}/remove")
    @RequiresPermission(code = "enrollment.manage_waitlist", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public void remove(@PathVariable WaitlistKind kind, @PathVariable Long enrollmentId, @Valid @RequestBody WaitlistReasonRequest request) {
        removeWaitlistEntryUseCase.remove(kind, enrollmentId, request.reason());
    }
}
