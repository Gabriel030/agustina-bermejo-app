package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsRefusals;
import com.red_escuela.backend.graduation.procedure.exception.IssuanceLimitReachedException;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureDocumentNotFoundException;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureNotEligibleException;
import org.springframework.stereotype.Component;

@Component
public class ApprovedSubjectsProcedureRefusals implements ApprovedSubjectsRefusals {

    @Override
    public RuntimeException enrollmentNotFound() {
        return new ProcedureDocumentNotFoundException();
    }

    @Override
    public RuntimeException notEligible() {
        return new ProcedureNotEligibleException();
    }

    @Override
    public RuntimeException issuanceLimitReached() {
        return new IssuanceLimitReachedException();
    }
}
