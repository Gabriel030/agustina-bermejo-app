package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Component
@Transactional(readOnly = true)
public class ProgramEnrollmentFinder {

    private final ProgramEnrollmentRepository programEnrollmentRepository;

    public ProgramEnrollmentFinder(ProgramEnrollmentRepository programEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
    }

    public ProgramEnrollment find(Long id) {
        return programEnrollmentRepository.findById(id)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(id));
    }

    public ProgramEnrollment requireOwnedBy(Long id, Long studentId) {
        ProgramEnrollment enrollment = find(id);
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new ProgramEnrollmentNotFoundException(id);
        }
        return enrollment;
    }

    public ProgramEnrollment requireById(Long id) {
        return find(id);
    }

    public EnrollmentBillingView requireBillingViewById(Long id) {
        ProgramEnrollment enrollment = find(id);
        Long templateId = programEnrollmentRepository.findBillingTemplateRef(id)
                .map(ProgramEnrollmentRepository.BillingTemplateRef::getTemplateId).orElse(null);
        return new EnrollmentBillingView(enrollment.getId(), enrollment.getStudent().getId(), enrollment.getProgram().getId(), null, templateId,
                enrollment.getStatus(), null, enrollment.getWithdrawalEffectiveDate(), enrollment.getWithdrawalRegisteredOn());
    }

    public List<Long> findIdsByStudentId(Long studentId) {
        return programEnrollmentRepository.findByStudentId(studentId).stream().map(ProgramEnrollment::getId).toList();
    }

    public List<ProgramEnrollment> findByStudent(Long studentId) {
        return programEnrollmentRepository.findByStudentId(studentId);
    }

    public List<ProgramEnrollment> findAllById(Collection<Long> ids) {
        return programEnrollmentRepository.findAllById(ids);
    }
}
