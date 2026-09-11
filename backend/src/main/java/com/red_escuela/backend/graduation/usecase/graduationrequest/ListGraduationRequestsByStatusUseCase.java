package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;

import java.util.List;

public interface ListGraduationRequestsByStatusUseCase {
    List<GraduationRequest> list(GraduationRequestStatus status);
}
