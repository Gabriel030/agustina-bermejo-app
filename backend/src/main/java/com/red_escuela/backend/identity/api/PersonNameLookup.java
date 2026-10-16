package com.red_escuela.backend.identity.api;

import java.util.Collection;
import java.util.Map;

public interface PersonNameLookup {

    Map<Long, String> findFullNamesByPersonId(Collection<Long> personIds);
}
