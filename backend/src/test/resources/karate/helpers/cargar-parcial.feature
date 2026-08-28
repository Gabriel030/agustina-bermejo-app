Feature: Auxiliar — cargar un parcial (EXAM) sobre una cursada

  # Llamado vía karate.call desde mesas-de-examen.feature para dejar una cursada
  # en REGULAR sin repetir el mismo bloque de carga de notas en cada escenario.

  Scenario:
    Given url baseUrl
    And path '/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + docenteToken
    And request { type: 'EXAM', sequenceNumber: '#(seq)', date: '2026-04-01T00:00:00', score: '#(score)', maxScore: 10 }
    When method post
    Then status 201
