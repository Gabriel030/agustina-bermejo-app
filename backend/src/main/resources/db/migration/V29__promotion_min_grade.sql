-- Nota de corte de promoción sin final — atajo opcional por materia, independiente de
-- has_final (una materia puede tener final Y ofrecer promoción). NULL = no promociona,
-- siempre requiere final. Ver EvaluationScheme.promotionMinGrade. Sin CHECK de rango: mismo
-- criterio que min_regular_grade/min_approval_grade (V19), que tampoco lo tienen.
ALTER TABLE evaluation_scheme ADD COLUMN promotion_min_grade DOUBLE PRECISION;
