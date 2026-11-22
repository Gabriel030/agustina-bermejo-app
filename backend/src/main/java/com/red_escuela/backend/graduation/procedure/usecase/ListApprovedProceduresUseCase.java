package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.graduation.procedure.dto.ApprovedProcedureResponse;
import java.util.List;

public interface ListApprovedProceduresUseCase {

    List<ApprovedProcedureResponse> listApproved();
}
