package com.red_escuela.backend.graduation.certificate.source;

import com.red_escuela.backend.billing.api.StudentOutstandingDebtQuery;
import com.red_escuela.backend.graduation.specialist.GraduationDebtGate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GraduationDebtCheck {

    private final boolean blockOnDebt;
    private final GraduationDebtGate graduationDebtGate;
    private final StudentOutstandingDebtQuery studentOutstandingDebtQuery;

    public GraduationDebtCheck(@Value("${app.certificates.block-on-debt:false}") boolean blockOnDebt,
                               GraduationDebtGate graduationDebtGate,
                               StudentOutstandingDebtQuery studentOutstandingDebtQuery) {
        this.blockOnDebt = blockOnDebt;
        this.graduationDebtGate = graduationDebtGate;
        this.studentOutstandingDebtQuery = studentOutstandingDebtQuery;
    }

    public boolean hasDebt(Long studentId) {
        return blockOnDebt && graduationDebtGate.mustCheckDebt() && studentOutstandingDebtQuery.hasOutstandingDebt(studentId);
    }
}
