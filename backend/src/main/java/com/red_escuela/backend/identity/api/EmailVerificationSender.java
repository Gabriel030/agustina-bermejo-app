package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.service.EmailVerificationIssuedEvent;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class EmailVerificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationSender.class);
    private static final long EXPIRATION_HOURS = 48;

    private final StudentRepository studentRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final String frontendBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationSender(
            StudentRepository studentRepository,
            EmailVerificationTokenRepository tokenRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.studentRepository = studentRepository;
        this.tokenRepository = tokenRepository;
        this.eventPublisher = eventPublisher;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public void sendVerificationToStudent(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        Person person = student.getPerson();

        tokenRepository.deleteByPerson(person);

        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String clearToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setPerson(person);
        token.setTokenHash(TokenHashUtil.sha256Hex(clearToken));
        token.setExpiresAt(ApplicationTime.now().plusHours(EXPIRATION_HOURS));
        tokenRepository.save(token);

        String link = frontendBaseUrl + "/verificar-email?token=" + clearToken;
        eventPublisher.publishEvent(EmailVerificationIssuedEvent.issuedFor(person.getId(), link));
        log.info("Email de verificación enviado para student {}, person {}", studentId, person.getId());
    }
}
