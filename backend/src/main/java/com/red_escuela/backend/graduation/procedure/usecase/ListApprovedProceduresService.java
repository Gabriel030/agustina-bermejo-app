package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.graduation.procedure.dto.ApprovedProcedureResponse;
import com.red_escuela.backend.graduation.procedure.specialist.ApprovedProcedureSources;
import com.red_escuela.backend.shared.UseCase;
import java.util.Comparator;
import java.util.List;

@UseCase
public class ListApprovedProceduresService implements ListApprovedProceduresUseCase {

    private final ApprovedProcedureSources sources;
    private final CurrentActorFinder currentActorFinder;

    public ListApprovedProceduresService(ApprovedProcedureSources sources, CurrentActorFinder currentActorFinder) {
        this.sources = sources;
        this.currentActorFinder = currentActorFinder;
    }

    @Override
    public List<ApprovedProcedureResponse> listApproved() {
        Long studentId = currentActorFinder.currentStudentId();
        return sources.all().stream()
                .flatMap(source -> source.listOf(studentId).stream())
                .sorted(Comparator.comparing(ApprovedProcedureResponse::issuedAt).reversed()
                        .thenComparing(ApprovedProcedureResponse::id, Comparator.reverseOrder()))
                .toList();
    }
}
