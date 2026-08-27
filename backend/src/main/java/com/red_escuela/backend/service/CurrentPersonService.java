package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.exception.PersonNotFoundException;
import com.red_escuela.backend.repository.PersonRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Resuelve la Person autenticada a partir del "sub" del JWT (personId), nunca de un id de la
 * URL — mismo mecanismo que PersonProfileService.resolveAuthenticatedPerson, reutilizado por
 * varios servicios: quien registra pagos y decide aprobaciones (Administrativo/Director) es
 * una Person con rol, no necesariamente un Student o un Instructor (Spec Procesos
 * administrativos), y StaffService necesita saber qué rol tiene el actor para decidir qué
 * roles puede asignar (RF-AD-05/RF-DI-04).
 */
@Service
public class CurrentPersonService {

    private final PersonRepository personRepository;

    public CurrentPersonService(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    public Person resolveCurrentPerson() {
        Long personId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        return personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
    }
}
