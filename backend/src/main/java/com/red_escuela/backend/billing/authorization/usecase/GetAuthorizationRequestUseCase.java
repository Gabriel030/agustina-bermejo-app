package com.red_escuela.backend.billing.authorization.usecase;

import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestInboxReader.InboxDetail;

public interface GetAuthorizationRequestUseCase {
    InboxDetail getAuthorizationRequest(Long id);
}
