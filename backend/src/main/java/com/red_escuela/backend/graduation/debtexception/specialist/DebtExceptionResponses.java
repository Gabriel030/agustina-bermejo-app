package com.red_escuela.backend.graduation.debtexception.specialist;

import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionResponse;
import com.red_escuela.backend.graduation.debtexception.entity.DocumentDebtException;
import com.red_escuela.backend.identity.api.PersonNameFinder;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DebtExceptionResponses {

    private static final String UNKNOWN_NAME = "";

    private final PersonNameFinder personNameFinder;

    public DebtExceptionResponses(PersonNameFinder personNameFinder) {
        this.personNameFinder = personNameFinder;
    }

    public DebtExceptionResponse of(DocumentDebtException exception) {
        return all(List.of(exception)).get(0);
    }

    public List<DebtExceptionResponse> all(Collection<DocumentDebtException> exceptions) {
        Map<Long, String> students = personNameFinder.findFullNamesByStudentId(
                exceptions.stream().map(DocumentDebtException::getStudentId).distinct().toList());
        Map<Long, String> issuers = personNameFinder.findFullNamesByPersonId(
                exceptions.stream().map(DocumentDebtException::getIssuedById).distinct().toList());
        return exceptions.stream().map(exception -> new DebtExceptionResponse(exception.getId(), exception.getStudentId(),
                students.getOrDefault(exception.getStudentId(), UNKNOWN_NAME), exception.getDocumentType(), exception.getStatus(),
                exception.getReason(), issuers.getOrDefault(exception.getIssuedById(), UNKNOWN_NAME), exception.getIssuedAt(),
                exception.getExpiresAt(), exception.getUsedAt(), null, null)).toList();
    }
}
