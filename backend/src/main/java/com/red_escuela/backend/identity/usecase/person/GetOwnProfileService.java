package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;

// RF-AL-13: cualquier rol autenticado edita sus propios datos. El Person nunca se resuelve
// desde un id de la URL, sino del "sub" del JWT (vía CurrentPersonService) — así el endpoint
// no puede, ni por error de implementación, afectar a otra persona.
@UseCase
public class GetOwnProfileService implements GetOwnProfileUseCase {

    private final CurrentPersonService currentPersonService;

    public GetOwnProfileService(CurrentPersonService currentPersonService) {
        this.currentPersonService = currentPersonService;
    }

    @Override
    public Person getOwnProfile() {
        return currentPersonService.resolveCurrentPerson();
    }
}
