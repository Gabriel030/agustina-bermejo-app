package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.dto.InstructorSummaryResponse;
import com.red_escuela.backend.evaluation.dto.ExamBoardRegistrationSummaryResponse;
import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;
import com.red_escuela.backend.evaluation.entity.ExamBoardFinalResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExamBoardActaPdfWriterTest {

    private final ExamBoardActaPdfWriter writer = new ExamBoardActaPdfWriter();

    private ExamBoardResponse board() {
        return new ExamBoardResponse(5L, 1L, "Teorías Socioculturales", null, null, 2L, LocalDateTime.of(2026, 12, 10, 18, 30),
                null, null, "Aula 3", "SCHEDULED", List.of(7L), List.of(new InstructorSummaryResponse(7L, "Ñandú Pérez")), null, null, null);
    }

    private ExamBoardRegistrationSummaryResponse registrant(String name, String dni, String status, BigDecimal score, ExamBoardFinalResult result) {
        return new ExamBoardRegistrationSummaryResponse(1L, name, dni, "Carrera", 1, status, score, 1L, null, null, null, null, result, null);
    }

    private String textOf(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void actaListsEachRegistrantWithDniAndFinalScore() throws IOException {
        String text = textOf(writer.write(board(), List.of(
                registrant("Muñoz, Ana", "30111222", "GRADED", new BigDecimal("8.50"), ExamBoardFinalResult.PASSED),
                registrant("Núñez, Luis", "30333444", "GRADED", new BigDecimal("3.00"), ExamBoardFinalResult.FAILED))));

        assertThat(text).contains("ACTA DE MESA DE EXAMEN", "Teorías Socioculturales", "Ñandú Pérez", "Aula 3", "10/12/2026 18:30",
                "Muñoz, Ana", "30111222", "8.50", "Aprobado", "Núñez, Luis", "30333444", "3.00", "Desaprobado",
                "Firma del tribunal", "Firma de Secretaría Académica").doesNotContain("?", "�");
    }

    @Test
    void registrantWithoutScoreShowsDashAndAbsentStatusShowsAbsent() throws IOException {
        String text = textOf(writer.write(board(), List.of(registrant("Pérez, Juan", "30555666", "ABSENT", null, null))));

        assertThat(text).contains("Pérez, Juan", "Ausente");
    }

    @Test
    void aLongRegistrantListSpillsOverKeepingTheSignatureBlock() throws IOException {
        List<ExamBoardRegistrationSummaryResponse> many = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            many.add(registrant("Alumno " + i, "3000" + i, "REGISTERED", null, null));
        }
        byte[] pdf = writer.write(board(), many);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
        assertThat(textOf(pdf)).contains("Alumno 79", "Firma del tribunal");
    }
}
