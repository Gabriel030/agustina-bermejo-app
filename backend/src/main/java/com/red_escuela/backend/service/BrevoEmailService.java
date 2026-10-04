package com.red_escuela.backend.service;

import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class BrevoEmailService implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(BrevoEmailService.class);

    private final JavaMailSender mailSender;
    private final InstitutionRepository institutionRepository;
    private final String fromAddress;

    public BrevoEmailService(JavaMailSender mailSender, InstitutionRepository institutionRepository,
            @Value("${app.mail.from-address}") String fromAddress) {
        this.mailSender = mailSender;
        this.institutionRepository = institutionRepository;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendPasswordResetCode(Person person, String code) {
        String institutionName = institutionRepository.findAll().stream()
                .findFirst()
                .map(Institution::getName)
                .orElse("Red Escuela");

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(person.emailValue());
            helper.setSubject("Código para recuperar tu contraseña");
            helper.setText(buildHtml(institutionName, person.getName(), code), true);
            mailSender.send(message);
            log.info("Código de recuperación enviado a person {}", person.getId());
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el email de recuperación a person {}", person.getId(), e);
        }
    }

    @Override
    public void sendEmailVerification(Person person, String verificationLink) {
        String institutionName = institutionRepository.findAll().stream()
                .findFirst()
                .map(Institution::getName)
                .orElse("Red Escuela");

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(person.emailValue());
            helper.setSubject("Verificá tu email para completar tu preinscripción");
            helper.setText(buildVerificationHtml(institutionName, person.getName(), verificationLink), true);
            mailSender.send(message);
            log.info("Email de verificación enviado a person {}", person.getId());
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el email de verificación a person {}", person.getId(), e);
        }
    }

    private String buildVerificationHtml(String institutionName, String personName, String link) {
        return """
                <table role="presentation" width="100%%" style="background-color:#f4f4f7;padding:32px 0;">
                  <tr>
                    <td align="center">
                      <table role="presentation" width="480" style="background-color:#ffffff;border-radius:12px;padding:32px;font-family:Arial,sans-serif;">
                        <tr>
                          <td style="text-align:center;padding-bottom:16px;">
                            <h2 style="margin:0;color:#1f2937;">%s</h2>
                          </td>
                        </tr>
                        <tr>
                          <td style="color:#374151;font-size:15px;">
                            <p>Hola %s,</p>
                            <p>Hacé click en el botón de abajo para verificar tu dirección de email. El link es válido por 48 horas.</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="text-align:center;padding:24px 0;">
                            <a href="%s" style="background-color:#111827;color:#ffffff;text-decoration:none;padding:14px 28px;border-radius:8px;font-size:15px;font-weight:bold;">Verificar email</a>
                          </td>
                        </tr>
                        <tr>
                          <td style="color:#6b7280;font-size:13px;">
                            <p>Si no te preinscribiste en %s, podés ignorar este email.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
                """.formatted(institutionName, personName, link, institutionName);
    }

    private String buildHtml(String institutionName, String personName, String code) {
        return """
                <table role="presentation" width="100%%" style="background-color:#f4f4f7;padding:32px 0;">
                  <tr>
                    <td align="center">
                      <table role="presentation" width="480" style="background-color:#ffffff;border-radius:12px;padding:32px;font-family:Arial,sans-serif;">
                        <tr>
                          <td style="text-align:center;padding-bottom:16px;">
                            <h2 style="margin:0;color:#1f2937;">%s</h2>
                          </td>
                        </tr>
                        <tr>
                          <td style="color:#374151;font-size:15px;">
                            <p>Hola %s,</p>
                            <p>Usá este código para elegir una nueva contraseña. Vence en 15 minutos.</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="text-align:center;padding:24px 0;">
                            <span style="font-size:32px;font-weight:bold;letter-spacing:8px;color:#111827;">%s</span>
                          </td>
                        </tr>
                        <tr>
                          <td style="color:#6b7280;font-size:13px;">
                            <p>Si no pediste este código, podés ignorar este email.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
                """.formatted(institutionName, personName, code);
    }
}
