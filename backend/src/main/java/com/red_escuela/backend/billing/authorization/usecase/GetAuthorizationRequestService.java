package com.red_escuela.backend.billing.authorization.usecase;

import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestInboxReader;
import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestInboxReader.InboxDetail;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetAuthorizationRequestService implements GetAuthorizationRequestUseCase {
    private final AuthorizationRequestInboxReader reader;

    public GetAuthorizationRequestService(AuthorizationRequestInboxReader reader) {
        this.reader = reader;
    }

    @Override
    public InboxDetail getAuthorizationRequest(Long id) {
        return reader.readDetail(id);
    }
}
