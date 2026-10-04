package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.GoogleAccountNotLinkedException;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.AuthTokenIssuer;
import com.red_escuela.backend.identity.infrastructure.GoogleIdTokenService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class LoginWithGoogleService implements LoginWithGoogleUseCase {
    private final GoogleIdTokenService googleIdTokenService;
    private final PersonRepository personRepository;
    private final AuthTokenIssuer authTokenIssuer;

    public LoginWithGoogleService(GoogleIdTokenService googleIdTokenService, PersonRepository personRepository,
                                   AuthTokenIssuer authTokenIssuer) {
        this.googleIdTokenService = googleIdTokenService;
        this.personRepository = personRepository;
        this.authTokenIssuer = authTokenIssuer;
    }

    @Override
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

        person.setGoogleSubjectId(profile.subject());
        personRepository.save(person);
        return authTokenIssuer.issueTokens(person);
    }

    private void requireAllowedToLogIn(Person person) {
        boolean locked = person.getLockedUntil() != null && person.getLockedUntil().isAfter(LocalDateTime.now());
        if (locked || person.getStatus() != StatusType.ACTIVE) {
            throw new InvalidCredentialsException();
        }
    }
}
