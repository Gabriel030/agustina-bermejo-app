package com.red_escuela.backend.entity;

import java.io.Serializable;
import java.util.Objects;

public class NotasId implements Serializable {
    private Long personaId;
    private Long cursoId;

    public NotasId() {}

    public NotasId(Long personaId, Long cursoId) {
        this.personaId = personaId;
        this.cursoId = cursoId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotasId)) return false;
        NotasId notasId = (NotasId) o;
        return Objects.equals(personaId, notasId.personaId) &&
                Objects.equals(cursoId, notasId.cursoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(personaId, cursoId);
    }
}
