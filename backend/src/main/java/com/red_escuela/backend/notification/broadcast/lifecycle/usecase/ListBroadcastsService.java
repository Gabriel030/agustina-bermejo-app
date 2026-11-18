package com.red_escuela.backend.notification.broadcast.lifecycle.usecase;

import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.lifecycle.dto.BroadcastSummaryResponse;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@UseCase
public class ListBroadcastsService implements ListBroadcastsUseCase {

    private final BroadcastRepository broadcastRepository;

    public ListBroadcastsService(BroadcastRepository broadcastRepository) {
        this.broadcastRepository = broadcastRepository;
    }

    @Override
    public PageResponse<BroadcastSummaryResponse> listBroadcasts(BroadcastStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), PageResponse.MAX_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Broadcast> result = status == null ? broadcastRepository.findAll(pageable) : broadcastRepository.findByStatus(status, pageable);
        return new PageResponse<>(result.map(BroadcastSummaryResponse::from).getContent(), pageable.getPageNumber(), pageable.getPageSize(),
                result.getTotalElements());
    }
}
