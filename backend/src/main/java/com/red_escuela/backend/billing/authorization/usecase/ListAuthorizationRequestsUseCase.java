package com.red_escuela.backend.billing.authorization.usecase;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestSearch;
import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestInboxReader.InboxPage;

public interface ListAuthorizationRequestsUseCase {
    InboxPage listAuthorizationRequests(AuthorizationRequestSearch search);
}
