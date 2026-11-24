package com.red_escuela.backend.enrollment.usecase.waitlist;

import com.red_escuela.backend.enrollment.dto.OwnWaitlistPositionResponse;
import com.red_escuela.backend.enrollment.service.WaitlistCandidate;
import com.red_escuela.backend.enrollment.service.WaitlistQueue;
import com.red_escuela.backend.enrollment.service.WaitlistQueueSnapshot;
import com.red_escuela.backend.enrollment.service.WaitlistQueues;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UseCase
public class ListOwnWaitlistPositionsService implements ListOwnWaitlistPositionsUseCase {

    private final WaitlistQueues waitlistQueues;

    public ListOwnWaitlistPositionsService(WaitlistQueues waitlistQueues) {
        this.waitlistQueues = waitlistQueues;
    }

    @Override
    public List<OwnWaitlistPositionResponse> list(Long studentId) {
        return waitlistQueues.all().stream()
                .flatMap(queue -> queue.findQueues(null, null).stream()
                        .flatMap(snapshot -> positionsOfStudentIn(queue, snapshot, studentId)))
                .toList();
    }

    private Stream<OwnWaitlistPositionResponse> positionsOfStudentIn(WaitlistQueue queue, WaitlistQueueSnapshot snapshot, Long studentId) {
        List<WaitlistCandidate> waiting = queue.findWaitingCandidates(snapshot.id());
        return IntStream.range(0, waiting.size())
                .filter(index -> studentId.equals(waiting.get(index).studentId()))
                .mapToObj(index -> new OwnWaitlistPositionResponse(queue.kind(), snapshot.id(), snapshot.name(),
                        waiting.get(index).enrollmentId(), index + 1, waiting.size(), snapshot.nextExpiry()));
    }
}
