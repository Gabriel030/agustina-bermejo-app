-- final_grade_type pasa de texto libre a un enum real (FinalGradeType: FINAL_EXAM/PROMOTION) —
-- antes se guardaba literalmente "FINAL" o "PROMOCION_DIRECTA" sin ninguna validación de
-- valores permitidos. Normaliza los datos existentes a los nuevos nombres antes de que la
-- entidad empiece a mapear la columna como @Enumerated(EnumType.STRING).
UPDATE subject_section_enrollment SET final_grade_type = 'FINAL_EXAM' WHERE final_grade_type = 'FINAL';
UPDATE subject_section_enrollment SET final_grade_type = 'PROMOTION' WHERE final_grade_type = 'PROMOCION_DIRECTA';
UPDATE course_section_enrollment SET final_grade_type = 'FINAL_EXAM' WHERE final_grade_type = 'FINAL';
UPDATE course_section_enrollment SET final_grade_type = 'PROMOTION' WHERE final_grade_type = 'PROMOCION_DIRECTA';
-- Defensivo: cualquier valor que haya quedado (datos de prueba/demo con texto libre previo a
-- esta migración, ajenos a los dos literales que el código realmente escribía) se limpia a
-- NULL en vez de romper el CHECK de abajo.
UPDATE subject_section_enrollment SET final_grade_type = NULL WHERE final_grade_type NOT IN ('FINAL_EXAM', 'PROMOTION');
UPDATE course_section_enrollment SET final_grade_type = NULL WHERE final_grade_type NOT IN ('FINAL_EXAM', 'PROMOTION');

ALTER TABLE subject_section_enrollment
    ADD CONSTRAINT chk_ssenrollment_final_grade_type CHECK (final_grade_type IS NULL OR final_grade_type IN ('FINAL_EXAM', 'PROMOTION'));
ALTER TABLE course_section_enrollment
    ADD CONSTRAINT chk_csenrollment_final_grade_type CHECK (final_grade_type IS NULL OR final_grade_type IN ('FINAL_EXAM', 'PROMOTION'));
