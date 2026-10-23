package com.red_escuela.backend.billing.collections.controller;

import com.red_escuela.backend.billing.collections.dto.OverdueContactPageResponse;
import com.red_escuela.backend.billing.collections.usecase.ListOverdueContactsUseCase;
import com.red_escuela.backend.shared.domain.Currency;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/collection-reminders")
public class OverdueContactListController {
    private final ListOverdueContactsUseCase listOverdueContactsUseCase;

    public OverdueContactListController(ListOverdueContactsUseCase listOverdueContactsUseCase) {
        this.listOverdueContactsUseCase = listOverdueContactsUseCase;
    }

    @GetMapping("/contact-list")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public OverdueContactPageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) Currency currency) {
        return listOverdueContactsUseCase.listOverdueContacts(page, size, currency);
    }
}
