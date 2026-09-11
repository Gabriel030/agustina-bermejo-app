package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.entity.Person;

import java.util.List;

public interface ListStaffUseCase {
    List<Person> list(ListStaffCommand command);
}
