package com.red_escuela.backend.evaluation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import com.red_escuela.backend.academicstructure.entity.Instructor;

@Getter
@Setter
@Entity
@Table(name = "exam_board_instructor")
public class ExamBoardInstructor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_board_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ExamBoard examBoard;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private Instructor instructor;
}
