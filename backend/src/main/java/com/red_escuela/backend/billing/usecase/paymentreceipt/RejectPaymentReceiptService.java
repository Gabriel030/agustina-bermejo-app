package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.billing.repository.PaymentReceiptRepository;
import com.red_escuela.backend.billing.service.PaymentReceiptFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.PaymentReceiptRejectedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class RejectPaymentReceiptService implements RejectPaymentReceiptUseCase {
    private static final Logger log = LoggerFactory.getLogger(RejectPaymentReceiptService.class);
    private final PaymentReceiptRepository paymentReceiptRepository;
    private final PaymentReceiptFinder paymentReceiptFinder;
    private final ApplicationEventPublisher eventPublisher;

    public RejectPaymentReceiptService(PaymentReceiptRepository paymentReceiptRepository, PaymentReceiptFinder paymentReceiptFinder,
                                       ApplicationEventPublisher eventPublisher) {
        this.paymentReceiptRepository = paymentReceiptRepository;
        this.paymentReceiptFinder = paymentReceiptFinder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public PaymentReceipt reject(Long id, String motivo, Person decidedBy) {
        PaymentReceipt receipt = paymentReceiptFinder.findOrThrow(id);
        receipt.reject(decidedBy, motivo);
        PaymentReceipt saved = paymentReceiptRepository.save(receipt);
        eventPublisher.publishEvent(new PaymentReceiptRejectedEvent(saved));
        log.warn("Comprobante de pago rechazado: comprobante={}, factura={}", id, saved.getInvoice().getId());
        return saved;
    }
}
