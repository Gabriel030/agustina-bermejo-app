package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.exception.DirectorApprovalRequestNotFoundException;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DirectorApprovalRequestFinder {

    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;

    public DirectorApprovalRequestFinder(DirectorApprovalRequestRepository directorApprovalRequestRepository) {
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
    }

    public DirectorApprovalRequest findOrThrow(Long id) {
        return directorApprovalRequestRepository.findById(id)
                .orElseThrow(() -> new DirectorApprovalRequestNotFoundException(id));
    }

    public List<DirectorApprovalRequest> findByProgramEnrollmentId(Long programEnrollmentId) {
        return directorApprovalRequestRepository.findByProgramEnrollmentId(programEnrollmentId);
    }
}
