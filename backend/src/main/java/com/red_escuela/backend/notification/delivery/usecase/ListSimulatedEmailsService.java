package com.red_escuela.backend.notification.delivery.usecase;

import com.red_escuela.backend.identity.api.EmailRecipient;
import com.red_escuela.backend.identity.api.EmailRecipientLookup;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.dto.SimulatedEmailPageResponse;
import com.red_escuela.backend.notification.delivery.dto.SimulatedEmailResponse;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.mail.MaskedEmailAddress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class ListSimulatedEmailsService implements ListSimulatedEmailsUseCase {
    public static final int MAX_PAGE_SIZE = 100;
    private static final String NO_RECIPIENT_ADDRESS = "-";

    private final NotificationDeliveryRepository deliveryRepository;
    private final EmailRecipientLookup emailRecipientLookup;

    public ListSimulatedEmailsService(NotificationDeliveryRepository deliveryRepository, EmailRecipientLookup emailRecipientLookup) {
        this.deliveryRepository = deliveryRepository;
        this.emailRecipientLookup = emailRecipientLookup;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public SimulatedEmailPageResponse listSimulatedEmails(int page, int size) {
        int boundedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int boundedPage = Math.max(page, 0);
        Page<NotificationDelivery> deliveries = deliveryRepository.findByChannel(DeliveryChannel.EMAIL,
                PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new SimulatedEmailPageResponse(deliveries.getContent().stream().map(this::toResponse).toList(), boundedPage, boundedSize,
                deliveries.getTotalElements());
    }

    private SimulatedEmailResponse toResponse(NotificationDelivery delivery) {
        String masked = emailRecipientLookup.findEmailRecipient(delivery.getRecipientPersonId())
                .map(EmailRecipient::address).map(MaskedEmailAddress::of).orElse(NO_RECIPIENT_ADDRESS);
        return SimulatedEmailResponse.from(delivery, masked);
    }
}
