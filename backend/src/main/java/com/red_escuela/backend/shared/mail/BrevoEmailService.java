package com.red_escuela.backend.shared.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@Profile("!test")
public class BrevoEmailService implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(BrevoEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public BrevoEmailService(JavaMailSender mailSender,
            @Value("${app.mail.from-address}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendPasswordResetCode(MailRecipient recipient, String institutionName, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipient.email());
            helper.setSubject("Código para recuperar tu contraseña");
            helper.setText(buildHtml(institutionName, recipient.name(), code), true);
            mailSender.send(message);
            log.info("Código de recuperación enviado a person {}", recipient.personId());
        } catch (MessagingException | RuntimeException e) { if (e instanceof com.red_escuela.backend.shared.exception.CircuitOpenException open) { throw open; }
            log.error("No se pudo enviar el email de recuperación a person {}", recipient.personId(), e);
            throw new EmailDeliveryException("No se pudo enviar el email de recuperación", e);
        }
    }

    @Override
    public void sendEmailVerification(MailRecipient recipient, String institutionName, String verificationLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipient.email());
            helper.setSubject("Verificá tu email para completar tu preinscripción");
            helper.setText(buildVerificationHtml(institutionName, recipient.name(), verificationLink), true);
            mailSender.send(message);
            log.info("Email de verificación enviado a person {}", recipient.personId());
        } catch (MessagingException | RuntimeException e) { if (e instanceof com.red_escuela.backend.shared.exception.CircuitOpenException open) { throw open; }
            log.error("No se pudo enviar el email de verificación a person {}", recipient.personId(), e);
            throw new EmailDeliveryException("No se pudo enviar el email de verificación", e);
        }
    }

    @Override
    public void sendEmailChangeVerification(String recipientName, String newEmail, String verificationLink) {
        String body = "<p>Hola " + HtmlUtils.htmlEscape(recipientName) + ",</p>"
                + "<p>Se pidió usar esta dirección como email de tu cuenta. Confirmala con el botón de abajo. El link es válido por 48 horas.</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(verificationLink) + "\">Confirmar nuevo email</a></p>"
                + "<p>Si no fuiste vos, podés ignorar este email: no se cambia nada hasta que confirmes.</p>";
        sendChangeMessage(newEmail, "Confirmá tu nuevo email", body);
    }

    @Override
    public void sendEmailChangeNotice(String recipientName, String previousEmail, String newEmail) {
        String body = "<p>Hola " + HtmlUtils.htmlEscape(recipientName) + ",</p>"
                + "<p>Se pidió cambiar el email de tu cuenta a " + HtmlUtils.htmlEscape(newEmail)
                + ". El cambio recién se aplica cuando se confirma desde esa dirección; hasta entonces seguís usando este email.</p>"
                + "<p>Si no fuiste vos, avisá a la institución y cambiá tu contraseña.</p>";
        sendChangeMessage(previousEmail, "Se pidió cambiar el email de tu cuenta", body);
    }

    @Override
    public void sendGuardianLegalConfirmation(String guardianName, String guardianEmail, String minorFirstName,
                                              String institutionName, String confirmationLink) {
        String body = "<p>Hola " + HtmlUtils.htmlEscape(guardianName) + ",</p>"
                + "<p>" + HtmlUtils.htmlEscape(minorFirstName) + " se registró en " + HtmlUtils.htmlEscape(institutionName)
                + " y, por ser menor de edad, necesitamos que vos, como responsable, confirmes que leíste y aceptás los términos y condiciones y la política de privacidad.</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(confirmationLink) + "\">Leer y confirmar</a></p>"
                + "<p>El enlace es de un solo uso y vence en pocos días. Solo sirve para esta confirmación: no da acceso a la cuenta ni a otros datos.</p>"
                + "<p>Si no esperabas este mensaje, ignoralo: no se confirma nada.</p>";
        sendChangeMessage(guardianEmail, "Confirmá la aceptación de los términos", body);
    }

    @Override
    public void sendPasswordResetLockoutNotice(String recipientName, String email) {
        String body = "<p>Hola " + HtmlUtils.htmlEscape(recipientName) + ",</p>"
                + "<p>Hubo varios intentos fallidos de recuperar la contraseña de tu cuenta.</p>"
                + "<p>Si fuiste vos, esperá un rato antes de volver a intentarlo. Si no fuiste vos, ignorá este mensaje: tu contraseña no cambió.</p>";
        sendChangeMessage(email, "Intentos fallidos de recuperar tu contraseña", body);
    }

    private void sendChangeMessage(String recipient, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Aviso de cambio de email enviado");
        } catch (MessagingException | RuntimeException e) { if (e instanceof com.red_escuela.backend.shared.exception.CircuitOpenException open) { throw open; }
            log.error("No se pudo enviar el aviso de cambio de email", e);
            throw new EmailDeliveryException("No se pudo enviar el aviso de cambio de email", e);
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
