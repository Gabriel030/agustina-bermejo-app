package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateDiscountRequest;
import com.red_escuela.backend.entity.Discount;
import com.red_escuela.backend.entity.DiscountType;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.exception.StudentNotFoundException;
import com.red_escuela.backend.repository.DiscountRepository;
import com.red_escuela.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Beca/descuento individual sobre un alumno puntual — se suma con cualquier
 * {@link com.red_escuela.backend.entity.Promotion} vigente que también le
 * aplique (decisión de negocio, ver {@link PricingCalculator}).
 */
@Service
public class DiscountService {

    private final DiscountRepository discountRepository;
    private final StudentRepository studentRepository;

    public DiscountService(DiscountRepository discountRepository, StudentRepository studentRepository) {
        this.discountRepository = discountRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional
    public Discount crear(Long studentId, CreateDiscountRequest request, Person createdBy) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));

        Discount discount = new Discount();
        discount.setStudent(student);
        discount.setTipo(request.tipo());
        // BECA_TOTAL no necesita valor — el cálculo la trata como 100% sin leer este campo.
        discount.setValor(request.tipo() == DiscountType.BECA_TOTAL ? java.math.BigDecimal.ZERO : request.valor());
        discount.setVigenteDesde(request.vigenteDesde());
        discount.setVigenteHasta(request.vigenteHasta());
        discount.setMotivo(request.motivo());
        discount.setCreatedBy(createdBy);
        discount.setCreatedAt(LocalDateTime.now());
        return discountRepository.save(discount);
    }

    public List<Discount> listarPorAlumno(Long studentId) {
        return discountRepository.findByStudentId(studentId);
    }

    List<Discount> vigentesParaAlumno(Long studentId, LocalDate fecha) {
        return discountRepository.findByStudentId(studentId).stream()
                .filter(discount -> discount.vigenteEl(fecha))
                .toList();
    }
}
