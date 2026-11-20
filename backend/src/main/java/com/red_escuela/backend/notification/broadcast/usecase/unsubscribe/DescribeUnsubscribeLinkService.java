package com.red_escuela.backend.notification.broadcast.usecase.unsubscribe;

import com.red_escuela.backend.identity.api.PersonExistenceChecker;
import com.red_escuela.backend.notification.broadcast.domain.UnsubscribeClaims;
import com.red_escuela.backend.notification.broadcast.exception.InvalidUnsubscribeTokenException;
import com.red_escuela.backend.notification.broadcast.dto.UnsubscribeLinkResponse;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeTokenVerifier;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DescribeUnsubscribeLinkService implements DescribeUnsubscribeLinkUseCase {

    private final UnsubscribeTokenVerifier unsubscribeTokenVerifier;
    private final PersonExistenceChecker personExistenceChecker;

    public DescribeUnsubscribeLinkService(UnsubscribeTokenVerifier unsubscribeTokenVerifier, PersonExistenceChecker personExistenceChecker) {
        this.personExistenceChecker = personExistenceChecker;
        this.unsubscribeTokenVerifier = unsubscribeTokenVerifier;
    }

    @Override
    public UnsubscribeLinkResponse describeUnsubscribeLink(String token) {
        UnsubscribeClaims claims = unsubscribeTokenVerifier.verifyToken(token);
        if (!personExistenceChecker.personExists(claims.personId())) {
            throw new InvalidUnsubscribeTokenException();
        }
        return new UnsubscribeLinkResponse(claims.channel(), claims.category());
    }
}
