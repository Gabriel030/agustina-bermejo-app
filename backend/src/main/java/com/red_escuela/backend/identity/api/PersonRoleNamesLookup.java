package com.red_escuela.backend.identity.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface PersonRoleNamesLookup {

    Map<Long, List<String>> findRoleNamesByPersonId(Collection<Long> personIds);
}
