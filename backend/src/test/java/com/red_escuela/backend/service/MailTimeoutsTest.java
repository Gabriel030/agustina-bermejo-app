package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MailTimeoutsTest extends AbstractIntegrationTest {
    @Autowired
    private JavaMailSender mailSender;

    @Test
    void smtpConnectionReadAndWriteHaveTimeoutsSoASlowServerCannotHangARequest() {
        var properties = ((JavaMailSenderImpl) mailSender).getJavaMailProperties();

        assertThat(properties.getProperty("mail.smtp.connectiontimeout")).isNotBlank();
        assertThat(properties.getProperty("mail.smtp.timeout")).isNotBlank();
        assertThat(properties.getProperty("mail.smtp.writetimeout")).isNotBlank();
    }
}
