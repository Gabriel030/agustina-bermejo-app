package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.identity.api.IssuedActivationToken;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.mail.TransactionalEmail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class ActivationInvitationEmailFactory {

    private static final String SUBJECT = "Activá tu cuenta";
    private static final String ACTIVATION_PATH = "/activar?token=";
    private static final DateTimeFormatter EXPIRATION_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String frontendBaseUrl;

    public ActivationInvitationEmailFactory(@Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public DeliveryContent createAuditContent() {
        return DeliveryContent.forEmail(SUBJECT,
                "Invitación para activar la cuenta. El enlace de activación viaja solo en el mail y no se guarda.", null);
    }

    public TransactionalEmail createActivationInvitation(EmailAddress recipient, IssuedActivationToken activation) {
        String link = frontendBaseUrl + ACTIVATION_PATH + activation.token();
        String expiration = activation.expiresAt().format(EXPIRATION_FORMAT);
        String text = "Hola,\n\nTe invitamos a activar tu cuenta para ingresar a la plataforma. "
                + "Entrá al siguiente enlace y elegí tu contraseña:\n\n" + link
                + "\n\nEl enlace es de un solo uso y vence el " + expiration
                + ". Si no esperabas este mail, podés ignorarlo.\n";
        String html = "<p>Hola,</p><p>Te invitamos a activar tu cuenta para ingresar a la plataforma. "
                + "Entrá al siguiente enlace y elegí tu contraseña:</p><p><a href=\"" + link + "\">Activar mi cuenta</a></p>"
                + "<p>El enlace es de un solo uso y vence el " + expiration
                + ". Si no esperabas este mail, podés ignorarlo.</p>";
        return new TransactionalEmail(recipient, SUBJECT, text, html);
    }
}
