package com.red_escuela.backend.shared.mail;

import com.red_escuela.backend.shared.resilience.CircuitBreaker;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.io.InputStream;

public class CircuitBreakingMailSender implements JavaMailSender {
    private final JavaMailSender delegate;
    private final CircuitBreaker breaker;

    public CircuitBreakingMailSender(JavaMailSender delegate, CircuitBreaker breaker) {
        this.delegate = delegate;
        this.breaker = breaker;
    }

    public JavaMailSender delegate() {
        return delegate;
    }

    @Override
    public MimeMessage createMimeMessage() {
        return delegate.createMimeMessage();
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
        return delegate.createMimeMessage(contentStream);
    }

    @Override
    public void send(MimeMessage mimeMessage) throws MailException {
        guarded(() -> delegate.send(mimeMessage));
    }

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {
        guarded(() -> delegate.send(mimeMessages));
    }

    @Override
    public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
        guarded(() -> delegate.send(mimeMessagePreparator));
    }

    @Override
    public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
        guarded(() -> delegate.send(mimeMessagePreparators));
    }

    @Override
    public void send(SimpleMailMessage simpleMessage) throws MailException {
        guarded(() -> delegate.send(simpleMessage));
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
        guarded(() -> delegate.send(simpleMessages));
    }

    private void guarded(Runnable send) {
        breaker.execute(() -> {
            send.run();
            return null;
        }, MailCircuit::countsAsProviderFailure);
    }
}
