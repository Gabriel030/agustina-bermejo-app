package com.red_escuela.backend.billing.payment.repository;

import com.red_escuela.backend.billing.payment.domain.Payment;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends Repository<Payment, Long> {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    List<Payment> findByStudentCurrentAccountIdInOrderByRegisteredAtDescIdDesc(Collection<Long> studentCurrentAccountIds);
}
