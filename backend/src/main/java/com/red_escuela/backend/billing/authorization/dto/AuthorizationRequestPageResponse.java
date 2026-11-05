package com.red_escuela.backend.billing.authorization.dto;

import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestInboxReader.InboxPage;

import java.util.List;

public record AuthorizationRequestPageResponse(List<AuthorizationRequestListItemResponse> items, int page, int size, long totalElements) {

    public static AuthorizationRequestPageResponse from(InboxPage page) {
        return new AuthorizationRequestPageResponse(page.entries().stream().map(entry -> AuthorizationRequestListItemResponse.from(entry, page.personNames())).toList(),
                page.page(), page.size(), page.totalElements());
    }
}
