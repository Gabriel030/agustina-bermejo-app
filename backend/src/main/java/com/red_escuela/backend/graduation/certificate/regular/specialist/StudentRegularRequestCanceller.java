package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularCertificateRequestNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularCertificateRequestNotPendingException;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularCertificateRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentRegularRequestCanceller {

    private final RegularCertificateRequestRepository repository;

    public StudentRegularRequestCanceller(RegularCertificateRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void cancel(Long requestId, Long studentId) {
        RegularCertificateRequest request = repository.findByIdAndStudentId(requestId, studentId)
                .orElseThrow(() -> new RegularCertificateRequestNotFoundException());
        if (!request.isPending()) {
            throw new RegularCertificateRequestNotPendingException();
        }
        request.cancelByStudent();
        repository.save(request);
    }
}
