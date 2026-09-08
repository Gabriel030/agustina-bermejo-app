package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.exception.InvalidPaymentPlanScopeException;
import com.red_escuela.backend.billing.exception.UpfrontWindowClosedException;
import com.red_escuela.backend.exception.NotOwnEnrollmentException;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class PaymentPlanSelectionGuard {

    private final CurrentStudentService currentStudentService;

    public PaymentPlanSelectionGuard(CurrentStudentService currentStudentService) {
        this.currentStudentService = currentStudentService;
    }

    // Endpoint compartido (spec: "ADMINISTRATIVO|autoservicio alumno") — Optional.empty() de
    // findCurrentStudent() significa que el actor es staff (Administrativo/Director, sin fila
    // Student propia), que puede operar sobre cualquier matriculación sin restricción.
    public void validateOwnershipIfStudent(Long ownerStudentId, Long enrollmentId) {
        currentStudentService.findCurrentStudent().ifPresent(student -> {
            if (!student.getId().equals(ownerStudentId)) {
                throw new NotOwnEnrollmentException(student.getId(), enrollmentId);
            }
        });
    }

    public void validateUpfrontWindow(PaymentPlanOption option) {
        if (option.getCadence() != PaymentPlanCadence.UPFRONT) {
            return;
        }
        if (!option.isUpfrontWindowEnabled()) {
            throw new UpfrontWindowClosedException(option.getId());
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(option.getUpfrontWindowStart()) || today.isAfter(option.getUpfrontWindowEnd())) {
            throw new UpfrontWindowClosedException(option.getId());
        }
    }

    public void validateScope(Integer scopeMonths, Integer maxMonths) {
        if (scopeMonths == null || scopeMonths <= 0) {
            throw new InvalidPaymentPlanScopeException("scopeMonths must be a positive number");
        }
        if (maxMonths != null && scopeMonths > maxMonths) {
            throw new InvalidPaymentPlanScopeException("scopeMonths (" + scopeMonths + ") exceeds what remains of the term (" + maxMonths + ")");
        }
    }
}
