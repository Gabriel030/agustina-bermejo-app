package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.dto.StudentResponse;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface ListStudentsUseCase {
    PageResponse<StudentResponse> list(ListStudentsCommand command);
}
