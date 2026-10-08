package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.BankTransferReceiptResponse;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BankTransferReceiptResponseAssembler {

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    public BankTransferReceiptResponseAssembler(ProgramEnrollmentRepository programEnrollmentRepository, CourseEnrollmentRepository courseEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Transactional(readOnly = true)
    public BankTransferReceiptResponse assembleBankTransferReceiptResponse(BankTransferReceipt receipt) {
        Invoice invoice = receipt.getInvoice();
        String productName = invoice.findCourseEnrollmentId().flatMap(courseEnrollmentRepository::findById).map(e -> e.getCourse().getName())
                .or(() -> invoice.findProgramEnrollmentId().flatMap(programEnrollmentRepository::findById).map(e -> e.getProgram().getName()))
                .orElse(null);
        return BankTransferReceiptResponse.from(receipt, productName);
    }

    @Transactional(readOnly = true)
    public List<BankTransferReceiptResponse> assembleBankTransferReceiptResponses(List<BankTransferReceipt> receipts) {
        return receipts.stream().map(this::assembleBankTransferReceiptResponse).toList();
    }
}
