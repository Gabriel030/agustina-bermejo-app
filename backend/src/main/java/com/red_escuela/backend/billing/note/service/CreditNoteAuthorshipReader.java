package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.authorization.domain.RequestAuthorship;
import com.red_escuela.backend.billing.authorization.service.RequestAuthorshipReader;
import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.domain.CreditNoteAuthorshipTrace;
import com.red_escuela.backend.identity.api.PersonNameLookup;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CreditNoteAuthorshipReader {
    private final RequestAuthorshipReader authorshipReader;
    private final PersonNameLookup personNames;

    public CreditNoteAuthorshipReader(RequestAuthorshipReader authorshipReader, PersonNameLookup personNames) {
        this.authorshipReader = authorshipReader;
        this.personNames = personNames;
    }

    @Transactional(readOnly = true)
    public Map<Long, CreditNoteAuthorshipTrace> describeAuthorshipOf(List<CreditNote> notes) {
        Set<Long> requestIds = notes.stream().map(CreditNote::getAuthorizationRequestId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, RequestAuthorship> authorshipByRequest = authorshipReader.findAuthorshipByRequestId(requestIds);
        Set<Long> personIds = new HashSet<>();
        notes.forEach(note -> {
            RequestAuthorship authorship = authorshipByRequest.get(note.getAuthorizationRequestId());
            if (authorship == null) {
                personIds.add(note.getIssuedByPersonId());
                return;
            }
            personIds.add(authorship.requestedByPersonId());
            personIds.add(authorship.decidedByPersonId());
        });
        personIds.remove(null);
        Map<Long, String> names = personNames.findFullNamesByPersonId(personIds);
        Map<Long, CreditNoteAuthorshipTrace> traces = new HashMap<>();
        notes.forEach(note -> {
            RequestAuthorship authorship = authorshipByRequest.get(note.getAuthorizationRequestId());
            traces.put(note.getId(), authorship == null
                    ? new CreditNoteAuthorshipTrace(nameOf(names, note.getIssuedByPersonId()), null, null)
                    : new CreditNoteAuthorshipTrace(nameOf(names, authorship.requestedByPersonId()), nameOf(names, authorship.decidedByPersonId()), authorship.decidedAt()));
        });
        return traces;
    }

    private static String nameOf(Map<Long, String> names, Long personId) {
        return personId == null ? null : names.get(personId);
    }
}
