-- Aula de la mesa de examen — texto libre, nullable (todavía no está definido quién/cómo se
-- asigna). Ver ExamBoard.classroom.
ALTER TABLE exam_board ADD COLUMN classroom VARCHAR(255);
