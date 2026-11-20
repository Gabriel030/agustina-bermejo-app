package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.dto.EmailChangePreviewResponse;
import com.red_escuela.backend.identity.entity.ContactChangeLog;
import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.specialist.ContactChangeRecorder;
import com.red_escuela.backend.identity.specialist.OwnerEmailChangeApprover;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.exception.InvalidEmailVerificationTokenException;
import com.red_escuela.backend.shared.mail.MaskedEmailAddress;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

@Service
public class EmailChangeReviewer {

    private static final Logger log = LoggerFactory.getLogger(EmailChangeReviewer.class);

    private final EmailVerificationTokenRepository tokenRepository;
    private final PersonRepository personRepository;
    private final ContactChangeRecorder contactChangeRecorder;
    private final OwnerEmailChangeApprover ownerEmailChangeApprover;

    public EmailChangeReviewer(EmailVerificationTokenRepository tokenRepository, PersonRepository personRepository,
                               ContactChangeRecorder contactChangeRecorder, OwnerEmailChangeApprover ownerEmailChangeApprover) {
        this.tokenRepository = tokenRepository;
        this.personRepository = personRepository;
        this.contactChangeRecorder = contactChangeRecorder;
        this.ownerEmailChangeApprover = ownerEmailChangeApprover;
    }

    @Transactional(readOnly = true)
    public EmailChangePreviewResponse preview(String clearToken) {
        EmailVerificationToken token = findPending(clearToken);
        Person person = token.getPerson();
        ContactChangeLog request = contactChangeRecorder.lastEmailChangeRequest(person.getId())
                .orElseThrow(InvalidEmailVerificationTokenException::new);
        return new EmailChangePreviewResponse(MaskedEmailAddress.of(EmailAddress.of(person.emailValue())),
                MaskedEmailAddress.of(EmailAddress.of(token.getNewEmail())), roleOfRequester(request),
                request.getOccurredAt(), token.getExpiresAt());
    }

    @Transactional
    public void approve(String clearToken) {
        EmailVerificationToken token = findPending(clearToken);
        ownerEmailChangeApprover.approveAndSendVerification(token);
        log.info("Cambio de email aprobado por el dueño para person {}", token.getPerson().getId());
    }

    private EmailVerificationToken findPending(String clearToken) {
        return tokenRepository.findByCancelTokenHash(TokenHashUtil.sha256Hex(clearToken))
                .filter(candidate -> candidate.getNewEmail() != null && candidate.getPerson().getEmail() != null)
                .filter(candidate -> !candidate.getExpiresAt().isBefore(ApplicationTime.now()))
                .orElseThrow(InvalidEmailVerificationTokenException::new);
    }

    private String roleOfRequester(ContactChangeLog request) {
        return personRepository.findById(request.getActorPersonId())
                .flatMap(requester -> requester.getRoles().stream().map(Role::getName).min(Comparator.naturalOrder()))
                .orElse("UNKNOWN");
    }
}
