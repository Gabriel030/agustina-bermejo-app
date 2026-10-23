package com.red_escuela.backend.identity.api;

import java.util.Collection;
import java.util.Map;

public interface StudentNameLookup {

    Map<Long, String> findFullNamesByStudentId(Collection<Long> studentIds);
}
