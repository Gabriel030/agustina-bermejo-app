package com.red_escuela.backend.notification.delivery.infrastructure;

import com.red_escuela.backend.notification.delivery.domain.ProviderReceipt;
import com.red_escuela.backend.notification.delivery.specialist.WhatsAppProvider;
import com.red_escuela.backend.notification.delivery.specialist.WhatsAppProviderRejectedException;
import com.red_escuela.backend.notification.delivery.specialist.WhatsAppProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
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
@ConditionalOnProperty(prefix = "app.broadcast.providers.whatsapp", name = "provider", havingValue = "meta")
public class MetaCloudWhatsAppProvider implements WhatsAppProvider {

    private static final Logger log = LoggerFactory.getLogger(MetaCloudWhatsAppProvider.class);
    private static final int TOO_MANY_REQUESTS = 429;

    private final RestClient restClient;
    private final String messagesPath;

    @Autowired
    public MetaCloudWhatsAppProvider(
            @Value("${app.broadcast.providers.whatsapp.meta.base-url}") String baseUrl,
            @Value("${app.broadcast.providers.whatsapp.meta.api-version}") String apiVersion,
            @Value("${app.broadcast.providers.whatsapp.meta.phone-number-id:}") String phoneNumberId,
            @Value("${app.broadcast.providers.whatsapp.meta.access-token:}") String accessToken,
            @Value("${app.broadcast.providers.whatsapp.meta.connect-timeout-ms}") long connectTimeoutMillis,
            @Value("${app.broadcast.providers.whatsapp.meta.read-timeout-ms}") long readTimeoutMillis) {
        if (phoneNumberId == null || phoneNumberId.isBlank() || accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("app.broadcast.providers.whatsapp.provider=meta requires WHATSAPP_META_PHONE_NUMBER_ID and WHATSAPP_META_ACCESS_TOKEN");
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMillis)).build());
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMillis));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();
        this.messagesPath = "/" + apiVersion + "/" + phoneNumberId + "/messages";
    }

    @Override
    public ProviderReceipt sendTemplate(String to, String templateName, String languageCode, List<String> variables) {
        try {
            MetaSendResponse response = restClient.post()
                    .uri(messagesPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody(to, templateName, languageCode, variables))
                    .retrieve()
                    .body(MetaSendResponse.class);
            return toReceipt(response);
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            log.warn("Meta rechazo el mensaje de WhatsApp status={}", status);
            if (failure.getStatusCode().is4xxClientError() && status != TOO_MANY_REQUESTS) {
                throw new WhatsAppProviderRejectedException(status, errorCodeOf(failure));
            }
            throw new WhatsAppProviderUnavailableException("Meta fallo con status " + status, failure);
        } catch (ResourceAccessException failure) {
            log.warn("Meta no respondio causa={}", failure.getCause() == null ? "unknown" : failure.getCause().getClass().getSimpleName());
            throw new WhatsAppProviderUnavailableException("Meta no respondio a tiempo", failure);
        }
    }

    private ProviderReceipt toReceipt(MetaSendResponse response) {
        if (response == null || response.messages() == null || response.messages().isEmpty()
                || response.messages().get(0).id() == null || response.messages().get(0).id().isBlank()) {
            throw new WhatsAppProviderUnavailableException("Meta respondio sin id de mensaje", null);
        }
        return new ProviderReceipt(response.messages().get(0).id());
    }

    private String errorCodeOf(RestClientResponseException failure) {
        try {
            MetaErrorResponse body = failure.getResponseBodyAs(MetaErrorResponse.class);
            if (body != null && body.error() != null && body.error().code() != null) {
                return String.valueOf(body.error().code());
            }
        } catch (RuntimeException unreadable) {
            log.debug("El cuerpo de error de Meta no se pudo leer");
        }
        return "HTTP_" + failure.getStatusCode().value();
    }

    private Map<String, Object> requestBody(String to, String templateName, String languageCode, List<String> variables) {
        List<Map<String, String>> parameters = variables.stream().map(value -> Map.of("type", "text", "text", value)).toList();
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", languageCode));
        if (!parameters.isEmpty()) {
            template.put("components", List.of(Map.of("type", "body", "parameters", parameters)));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", to.startsWith("+") ? to.substring(1) : to);
        body.put("type", "template");
        body.put("template", template);
        return body;
    }

    private record MetaSendResponse(List<MetaMessage> messages) {
    }

    private record MetaMessage(String id) {
    }

    private record MetaErrorResponse(MetaError error) {
    }

    private record MetaError(Integer code) {
    }
}
