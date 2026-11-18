package com.red_escuela.backend.notification.broadcast.lifecycle.usecase;

import com.red_escuela.backend.identity.api.PersonNameFinder;
import com.red_escuela.backend.notification.broadcast.draft.exception.BroadcastNotFoundException;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.freezer.entity.BroadcastRecipient;
import com.red_escuela.backend.notification.broadcast.freezer.repository.BroadcastRecipientRepository;
import com.red_escuela.backend.notification.broadcast.lifecycle.dto.BroadcastRecipientResponse;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

@UseCase
public class ListBroadcastRecipientsService implements ListBroadcastRecipientsUseCase {

    private final BroadcastRepository broadcastRepository;
    private final BroadcastRecipientRepository recipientRepository;
    private final PersonNameFinder personNameFinder;

    public ListBroadcastRecipientsService(BroadcastRepository broadcastRepository, BroadcastRecipientRepository recipientRepository,
                                          PersonNameFinder personNameFinder) {
        this.broadcastRepository = broadcastRepository;
        this.recipientRepository = recipientRepository;
        this.personNameFinder = personNameFinder;
    }

    @Override
    public PageResponse<BroadcastRecipientResponse> listBroadcastRecipients(Long broadcastId, BroadcastRecipientStatus status, int page, int size) {
        if (!broadcastRepository.existsById(broadcastId)) {
            throw new BroadcastNotFoundException(broadcastId);
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), PageResponse.MAX_SIZE), Sort.by("id"));
        Page<BroadcastRecipient> result = status == null
                ? recipientRepository.findByBroadcastId(broadcastId, pageable)
                : recipientRepository.findByBroadcastIdAndStatus(broadcastId, status, pageable);
        Map<Long, String> names = personNameFinder.findFullNamesByPersonId(result.map(BroadcastRecipient::getPersonId).getContent());
        return new PageResponse<>(result.map(row -> new BroadcastRecipientResponse(row.getPersonId(), names.get(row.getPersonId()),
                row.getStatus(), row.getExclusionReason(), row.getAddressMasked())).getContent(),
                pageable.getPageNumber(), pageable.getPageSize(), result.getTotalElements());
    }
}
