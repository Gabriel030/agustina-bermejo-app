package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureStatusFact;
import com.red_escuela.backend.evaluation.domain.CommissionClosureListFilter;
import com.red_escuela.backend.evaluation.dto.CommissionClosureCountsResponse;
import com.red_escuela.backend.evaluation.dto.CommissionClosureListItemResponse;
import com.red_escuela.backend.evaluation.dto.CommissionClosurePageResponse;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.repository.CommissionClosureListRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureListRow;
import com.red_escuela.backend.evaluation.repository.CommissionClosureStatusCount;
import com.red_escuela.backend.shared.persistence.LikePatterns;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CommissionClosureListReader {

    private static final String INSTRUCTOR_SEPARATOR = "\u001f";

    private final CommissionClosureListRepository listRepository;
    private final Clock clock;

    public CommissionClosureListReader(CommissionClosureListRepository listRepository, Clock clock) {
        this.listRepository = listRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CommissionClosurePageResponse readPage(CommissionClosureListFilter filter, int page, int size) {
        ClosureStatus status = filter.status();
        Long termId = filter.termId();
        LocalDateTime now = LocalDateTime.now(clock);
        String pattern = patternOf(filter.query());
        Map<ClosureStatus, Long> totals = totalsByStatus(now, termId, pattern);
        CommissionClosureCountsResponse counts = countsOf(totals);
        long totalElements = status == null ? counts.all() : totals.get(status);
        List<CommissionClosureListItemResponse> items = totalElements == 0 || (long) page * size >= totalElements
                ? List.of()
                : listRepository.findPage(now, termId, pattern, status == null ? null : status.name(), size, (long) page * size)
                        .stream().map(CommissionClosureListReader::itemOf).toList();
        return new CommissionClosurePageResponse(items, page, size, totalElements, counts);
    }

    private Map<ClosureStatus, Long> totalsByStatus(LocalDateTime now, Long termId, String pattern) {
        Map<ClosureStatus, Long> totals = new EnumMap<>(ClosureStatus.class);
        for (ClosureStatus value : ClosureStatus.values()) {
            totals.put(value, 0L);
        }
        for (CommissionClosureStatusCount row : listRepository.countByStatus(now, termId, pattern)) {
            totals.put(ClosureStatus.valueOf(row.getStatus()), row.getTotal());
        }
        return totals;
    }

    private static CommissionClosureCountsResponse countsOf(Map<ClosureStatus, Long> totals) {
        long all = totals.values().stream().mapToLong(Long::longValue).sum();
        return new CommissionClosureCountsResponse(all, totals.get(ClosureStatus.TEACHER_CONFIRMED),
                totals.get(ClosureStatus.DRAFT_AVAILABLE), totals.get(ClosureStatus.CLOSED), totals.get(ClosureStatus.REOPENED));
    }

    private static String patternOf(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return LikePatterns.contains(query.trim().toLowerCase(Locale.ROOT));
    }

    private static CommissionClosureListItemResponse itemOf(CommissionClosureListRow row) {
        ClosureStatus status = ClosureStatus.valueOf(row.getStatus());
        boolean hasSnapshot = row.getSnapshotCount() != null && row.getSnapshotCount() > 0;
        boolean closureExists = row.getClosureId() != null;
        return new CommissionClosureListItemResponse(ClosureSectionKind.valueOf(row.getSectionKind()), row.getSectionId(),
                row.getClosureId(), row.getSubjectName(), row.getSectionName(), row.getTermId(), row.getTermLabel(),
                instructorNamesOf(row.getInstructorNames()), row.getStudentCount(),
                hasSnapshot && status != ClosureStatus.DRAFT_AVAILABLE ? row.getPromoteCount() : null,
                hasSnapshot ? row.getToReviewCount() : null, status, row.getStatusDate(), ClosureStatusFact.of(status),
                closureExists ? row.getReportNumber() : null, closureExists ? row.getReportVersion() : null,
                status == ClosureStatus.CLOSED ? row.getBook() : null, status == ClosureStatus.CLOSED ? row.getFolio() : null);
    }

    private static List<String> instructorNamesOf(String joined) {
        return joined == null || joined.isEmpty() ? List.of() : List.of(joined.split(INSTRUCTOR_SEPARATOR));
    }
}
