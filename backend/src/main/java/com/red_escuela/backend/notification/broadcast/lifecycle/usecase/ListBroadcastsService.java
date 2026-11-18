package com.red_escuela.backend.notification.broadcast.lifecycle.usecase;

import com.red_escuela.backend.identity.api.PersonNameFinder;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.lifecycle.dto.BroadcastSummaryResponse;
import com.red_escuela.backend.notification.broadcast.lifecycle.specialist.BroadcastRecipientCounts;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@UseCase
public class ListBroadcastsService implements ListBroadcastsUseCase {

    private final BroadcastRepository broadcastRepository;

    private final BroadcastRecipientCounts recipientCounts;
    private final PersonNameFinder personNameFinder;

    public ListBroadcastsService(BroadcastRepository broadcastRepository, BroadcastRecipientCounts recipientCounts,
                                 PersonNameFinder personNameFinder) {
        this.broadcastRepository = broadcastRepository;
        this.recipientCounts = recipientCounts;
        this.personNameFinder = personNameFinder;
    }

    @Override
    public PageResponse<BroadcastSummaryResponse> listBroadcasts(BroadcastStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), PageResponse.MAX_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Broadcast> result = status == null ? broadcastRepository.findAll(pageable) : broadcastRepository.findByStatus(status, pageable);
        List<Broadcast> rows = result.getContent();
        var counts = recipientCounts.countByBroadcast(rows);
        Map<Long, String> names = personNameFinder.findFullNamesByPersonId(rows.stream()
                .flatMap(row -> Stream.of(row.getCreatedByPersonId(), row.getConfirmedByPersonId())).filter(Objects::nonNull).distinct().toList());
        List<BroadcastSummaryResponse> content = rows.stream().map(row -> BroadcastSummaryResponse.from(row, names, counts.get(row.getId()))).toList();
        return new PageResponse<>(content, pageable.getPageNumber(), pageable.getPageSize(),
                result.getTotalElements());
    }
}
