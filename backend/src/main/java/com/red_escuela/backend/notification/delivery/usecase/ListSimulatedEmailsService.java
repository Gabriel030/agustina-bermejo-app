package com.red_escuela.backend.notification.delivery.usecase;

import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.dto.SimulatedEmailPageResponse;
import com.red_escuela.backend.notification.delivery.dto.SimulatedEmailResponse;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.delivery.service.SimulatedEmailAddressMaskFinder;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class ListSimulatedEmailsService implements ListSimulatedEmailsUseCase {
    public static final int MAX_PAGE_SIZE = 100;

    private final NotificationDeliveryRepository deliveryRepository;
    private final SimulatedEmailAddressMaskFinder maskFinder;

    public ListSimulatedEmailsService(NotificationDeliveryRepository deliveryRepository, SimulatedEmailAddressMaskFinder maskFinder) {
        this.deliveryRepository = deliveryRepository;
        this.maskFinder = maskFinder;
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
        String masked = maskFinder.findMaskedAddress(delivery.getRecipientPersonId());
        return SimulatedEmailResponse.from(delivery, masked);
    }
}
