package com.red_escuela.backend.notification.broadcast.usecase.unsubscribe;

import com.red_escuela.backend.notification.broadcast.domain.UnsubscribeClaims;
import com.red_escuela.backend.notification.broadcast.dto.UnsubscribeLinkResponse;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeTokenVerifier;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DescribeUnsubscribeLinkService implements DescribeUnsubscribeLinkUseCase {

    private final UnsubscribeTokenVerifier unsubscribeTokenVerifier;

    public DescribeUnsubscribeLinkService(UnsubscribeTokenVerifier unsubscribeTokenVerifier) {
        this.unsubscribeTokenVerifier = unsubscribeTokenVerifier;
    }

    @Override
    public UnsubscribeLinkResponse describeUnsubscribeLink(String token) {
        UnsubscribeClaims claims = unsubscribeTokenVerifier.verifyToken(token);
        return new UnsubscribeLinkResponse(claims.channel(), claims.category());
    }
}
