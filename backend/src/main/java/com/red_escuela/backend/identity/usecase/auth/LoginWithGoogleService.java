package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.GoogleAccountNotLinkedException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.AuthTokenIssuer;
import com.red_escuela.backend.identity.infrastructure.GoogleIdTokenService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

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
            return authTokenIssuer.issueTokens(byGoogleId.get());
        }

        Person person = personRepository.findByEmail(profile.email())
                .orElseThrow(() -> new GoogleAccountNotLinkedException(profile.email()));

        person.setGoogleSubjectId(profile.subject());
        personRepository.save(person);
        return authTokenIssuer.issueTokens(person);
    }
}
