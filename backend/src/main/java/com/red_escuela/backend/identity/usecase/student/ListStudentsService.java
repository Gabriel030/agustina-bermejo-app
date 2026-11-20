package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.dto.StudentResponse;
import com.red_escuela.backend.identity.specialist.StudentPageReader;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;

@UseCase
public class ListStudentsService implements ListStudentsUseCase {

    private final StudentPageReader studentPageReader;

    public ListStudentsService(StudentPageReader studentPageReader) {
        this.studentPageReader = studentPageReader;
    }

    @Override
    public PageResponse<StudentResponse> list(ListStudentsCommand command) {
        return studentPageReader.readPage(command.status(), command.onboardingStatus(), command.search(),
                command.page(), command.size(), command.includeWithdrawalReason());
    }
}
