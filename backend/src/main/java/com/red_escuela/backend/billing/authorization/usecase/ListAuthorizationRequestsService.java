package com.red_escuela.backend.billing.authorization.usecase;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestSearch;
import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestInboxReader;
import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestInboxReader.InboxPage;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListAuthorizationRequestsService implements ListAuthorizationRequestsUseCase {
    private final AuthorizationRequestInboxReader reader;

    public ListAuthorizationRequestsService(AuthorizationRequestInboxReader reader) {
        this.reader = reader;
    }

    @Override
    public InboxPage listAuthorizationRequests(AuthorizationRequestSearch search) {
        return reader.readPage(search);
    }
}
