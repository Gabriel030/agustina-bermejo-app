package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.GoogleAccountNotLinkedException;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.identity.policy.GoogleLinkByEmailPolicy;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.specialist.AuthTokenIssuer;
import com.red_escuela.backend.identity.specialist.PublicPasswordResetBlockPolicy;
import com.red_escuela.backend.identity.infrastructure.GoogleIdTokenService;
import org.springframework.stereotype.Service;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.transaction.annotation.Transactional;


@Service
public class GoogleLoginTransaction {
    private final GoogleIdTokenService googleIdTokenService;
    private final PersonRepository personRepository;
    private final AuthTokenIssuer authTokenIssuer;
    private final PublicPasswordResetBlockPolicy publicPasswordResetBlockPolicy;

    public GoogleLoginTransaction(GoogleIdTokenService googleIdTokenService, PersonRepository personRepository,
                                   AuthTokenIssuer authTokenIssuer,
                                   PublicPasswordResetBlockPolicy publicPasswordResetBlockPolicy) {
        this.googleIdTokenService = googleIdTokenService;
        this.personRepository = personRepository;
        this.authTokenIssuer = authTokenIssuer;
        this.publicPasswordResetBlockPolicy = publicPasswordResetBlockPolicy;
    }

    @Transactional
    public AuthTokens login(String idToken) {
        GoogleIdTokenService.GoogleProfile profile = googleIdTokenService.verify(idToken);

        var byGoogleId = personRepository.findByGoogleSubjectId(profile.subject());
        if (byGoogleId.isPresent()) {
            requireAllowedToLogIn(byGoogleId.get());
            return authTokenIssuer.issueTokens(byGoogleId.get());
        }

        Person person = personRepository.findByEmail(EmailAddress.of(profile.email()))
                .orElseThrow(() -> new GoogleAccountNotLinkedException(profile.email()));
        requireAllowedToLogIn(person);
        GoogleLinkByEmailPolicy.requireAllowed(person, publicPasswordResetBlockPolicy.blocks(person));

        person.setGoogleSubjectId(profile.subject());
        personRepository.save(person);
        return authTokenIssuer.issueTokens(person);
    }

    private void requireAllowedToLogIn(Person person) {
        boolean locked = person.getLockedUntil() != null && person.getLockedUntil().isAfter(ApplicationTime.now());
        if (locked || person.getStatus() != StatusType.ACTIVE) {
            throw new InvalidCredentialsException();
        }
    }
}
