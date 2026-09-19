package com.red_escuela.backend.identity.usecase.emailverification;

import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.service.EmailService;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.util.TokenHashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@UseCase
public class SendEmailVerificationService implements SendEmailVerificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(SendEmailVerificationService.class);
    private static final long EXPIRATION_HOURS = 48;

    private final StudentRepository studentRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final String frontendBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public SendEmailVerificationService(
            StudentRepository studentRepository,
            EmailVerificationTokenRepository tokenRepository,
            EmailService emailService,
            @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.studentRepository = studentRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    @Transactional
    public void sendVerification(Long studentId) {
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
        token.setExpiresAt(LocalDateTime.now().plusHours(EXPIRATION_HOURS));
        tokenRepository.save(token);

        String link = frontendBaseUrl + "/verificar-email?token=" + clearToken;
        emailService.sendEmailVerification(person, link);
        log.info("Email de verificación enviado para student {}, person {}", studentId, person.getId());
    }
}
