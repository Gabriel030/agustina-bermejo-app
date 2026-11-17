package com.red_escuela.backend.shared.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.mail.transactional", name = "provider", havingValue = "brevo")
public class BrevoTransactionalEmailGateway implements TransactionalEmailGateway {

    private static final Logger log = LoggerFactory.getLogger(BrevoTransactionalEmailGateway.class);
    private static final String SEND_PATH = "/v3/smtp/email";
    private static final String API_KEY_HEADER = "api-key";
    private static final String LIST_UNSUBSCRIBE_HEADER = "List-Unsubscribe";

    private final RestClient restClient;
    private final String senderAddress;
    private final String senderName;

    @Autowired
    public BrevoTransactionalEmailGateway(
            @Value("${app.mail.brevo.api-url}") String apiUrl,
            @Value("${app.mail.brevo.api-key:}") String apiKey,
            @Value("${app.mail.from-address}") String senderAddress,
            @Value("${app.mail.brevo.sender-name:}") String senderName,
            @Value("${app.mail.brevo.connect-timeout-ms}") long connectTimeoutMillis,
            @Value("${app.mail.brevo.read-timeout-ms}") long readTimeoutMillis) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("app.mail.transactional.provider=brevo requires the BREVO_API_KEY environment variable");
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMillis)).build());
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMillis));
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .requestFactory(requestFactory)
                .defaultHeader(API_KEY_HEADER, apiKey)
                .build();
        this.senderAddress = senderAddress;
        this.senderName = senderName;
    }

    @Override
    public EmailDeliveryReceipt send(TransactionalEmail email) {
        String maskedRecipient = MaskedEmailAddress.of(email.recipient());
        try {
            BrevoSendResponse response = restClient.post()
                    .uri(SEND_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody(email))
                    .retrieve()
                    .body(BrevoSendResponse.class);
            return toReceipt(response, maskedRecipient);
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            log.warn("Brevo rechazo el mail para {} status={}", maskedRecipient, status);
            if (failure.getStatusCode().is4xxClientError()) {
                throw new TransactionalEmailRejectedException("Brevo rechazo el mail con status " + status, status, failure);
            }
            throw new TransactionalEmailDeliveryException("Brevo fallo con status " + status, failure);
        } catch (ResourceAccessException failure) {
            log.warn("Brevo no respondio para {} causa={}", maskedRecipient, failure.getCause() == null ? "unknown" : failure.getCause().getClass().getSimpleName());
            throw new TransactionalEmailDeliveryException("Brevo no respondio a tiempo", failure);
        }
    }

    private EmailDeliveryReceipt toReceipt(BrevoSendResponse response, String maskedRecipient) {
        if (response == null || response.messageId() == null || response.messageId().isBlank()) {
            throw new TransactionalEmailDeliveryException("Brevo respondio sin messageId", null);
        }
        String messageId = response.messageId().length() > EmailDeliveryReceipt.MAX_PROVIDER_MESSAGE_ID_LENGTH
                ? response.messageId().substring(0, EmailDeliveryReceipt.MAX_PROVIDER_MESSAGE_ID_LENGTH)
                : response.messageId();
        log.info("Mail enviado por Brevo a {}", maskedRecipient);
        return new EmailDeliveryReceipt(messageId, false);
    }

    private Map<String, Object> requestBody(TransactionalEmail email) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, String> sender = new LinkedHashMap<>();
        sender.put("email", senderAddress);
        if (senderName != null && !senderName.isBlank()) {
            sender.put("name", senderName);
        }
        body.put("sender", sender);
        body.put("to", List.of(Map.of("email", email.recipient().value())));
        body.put("subject", email.subject());
        body.put("textContent", email.bodyText());
        if (email.bodyHtml() != null && !email.bodyHtml().isBlank()) {
            body.put("htmlContent", email.bodyHtml());
        }
        if (email.unsubscribeUrl() != null && !email.unsubscribeUrl().isBlank()) {
            body.put("headers", Map.of(LIST_UNSUBSCRIBE_HEADER, "<" + email.unsubscribeUrl() + ">"));
        }
        return body;
    }

    private record BrevoSendResponse(String messageId) {
    }
}
