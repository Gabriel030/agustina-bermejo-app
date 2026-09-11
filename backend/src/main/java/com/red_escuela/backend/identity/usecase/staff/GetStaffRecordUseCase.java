package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.entity.Person;

public interface GetStaffRecordUseCase {
    Person getRecord(Long personId);
}
