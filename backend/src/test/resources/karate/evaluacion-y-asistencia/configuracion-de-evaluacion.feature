Feature: Configuración de evaluación, esquemas, notas y mesas — contrato de errores (QA ronda 3, P4)

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def directorToken = fixtures.tokenForRole('DIRECTOR')

    * def nombreMateria = 'Materia QA config ' + java.lang.System.nanoTime()
    * def codigoMateria = Java.type('com.red_escuela.backend.config.TestSubjectCodes').next()
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateria)', code: '#(codigoMateria)' }
    When method post
    Then status 201
    * def subjectId = response.id

    * def docente = fixtures.createTeacherWithToken()
    * def evaluationSchemeId = fixtures.createEvaluationScheme()

    * def crearComision =
    """
    function(startDate, endDate) {
      var term = karate.call('classpath:karate/helpers/crear-comision-con-titular.feature',
        { baseUrl: baseUrl, adminToken: adminToken, subjectId: subjectId, evaluationSchemeId: evaluationSchemeId,
          instructorId: docente.instructorId, startDate: startDate, endDate: endDate });
      return term;
    }
    """

  Scenario: Mis comisiones — cada comisión dictada trae el cierre de su ciclo
    * def comision = crearComision('2024-03-04T00:00:00', '2024-12-13T00:00:00')

    Given path '/me/teaching-sections'
    And header Authorization = 'Bearer ' + docente.accessToken
    When method get
    Then status 200
    And match response[*].id contains comision.sectionId
    And match response[0].termEndDate == '2024-12-13T00:00:00'

  Scenario: Ciclo ya iniciado — el borrador no se puede enviar y la respuesta lo dice con canSubmit y el código del error
    * def comision = crearComision('2026-01-05T00:00:00', '2026-12-11T00:00:00')

    Given path '/me/section-evaluation-configs', comision.sectionId
    And header Authorization = 'Bearer ' + docente.accessToken
    When method get
    Then status 200
    And match response.canEdit == true
    And match response.canSubmit == false

    Given path '/me/section-evaluation-configs', comision.sectionId, 'submit'
    And header Authorization = 'Bearer ' + docente.accessToken
    When method post
    Then status 400
    And match response.code == 'SUBMISSION_WINDOW_CLOSED'

  Scenario: Fechas inválidas — el código distingue fuera del ciclo de feriado
    * def comision = crearComision('2026-01-05T00:00:00', '2026-12-11T00:00:00')

    Given path '/me/section-evaluation-configs', comision.sectionId
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { tpCount: 1, tp1DueDate: '2026-04-24', exam1Date: '2026-05-15', exam2Date: '2026-09-11', finalCall1Date: '2026-12-02', finalCall2Date: '2026-12-16' }
    When method put
    Then status 400
    And match response.code == 'EVALUATION_DATE_OUTSIDE_TERM'

    Given path '/me/section-evaluation-configs', comision.sectionId
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { tpCount: 1, tp1DueDate: '2026-04-24', exam1Date: '2026-05-25', exam2Date: '2026-09-11', finalCall1Date: '2026-12-02', finalCall2Date: '2026-12-10' }
    When method put
    Then status 400
    And match response.code == 'EVALUATION_DATE_ON_HOLIDAY'

  Scenario: Ciclo por empezar — el borrador se puede enviar
    * def comision = crearComision('2099-03-02T00:00:00', '2099-12-11T00:00:00')

    Given path '/me/section-evaluation-configs', comision.sectionId
    And header Authorization = 'Bearer ' + docente.accessToken
    When method get
    Then status 200
    And match response.canSubmit == true

  Scenario Outline: Esquema de evaluación con notas fuera de escala o incoherentes — 400 con su código
    Given path '/evaluation-schemes'
    And header Authorization = 'Bearer ' + directorToken
    And request { name: 'Esquema QA inválido', examCountExpected: <parciales>, hasFinal: true, minRegularGrade: <regular>, minApprovalGrade: <aprobar>, promotionMinGrade: <promocion> }
    When method post
    Then status 400
    And match response.code == '<codigo>'

    Examples:
      | parciales | regular | aprobar | promocion | codigo                    |
      | 2         | -2      | 6       | null      | INVALID_GRADE             |
      | 2         | 4       | 15      | null      | INVALID_GRADE             |
      | 2         | 4       | 6       | 3         | INVALID_EVALUATION_SCHEME |
      | 2         | 6       | 4       | null      | INVALID_EVALUATION_SCHEME |
      | 5         | 4       | 6       | null      | INVALID_EVALUATION_SCHEME |

  Scenario: Nota mayor a la nota máxima — 400 INVALID_GRADE
    * def comision = crearComision('2026-01-05T00:00:00', '2026-12-11T00:00:00')
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())
    * def nombrePrograma = 'Carrera QA config ' + java.lang.System.nanoTime()
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectId)', category: 'MANDATORY', curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)' }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

    Given path '/program-enrollments', programEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { sectionId: '#(comision.sectionId)' }
    When method post
    Then status 201
    * def sectionEnrollmentId = response.id

    Given path '/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { type: 'TP', sequenceNumber: 1, date: '2026-05-15T00:00:00', score: 15 }
    When method post
    Then status 400
    And match response.code == 'INVALID_GRADE'

  Scenario: Mesas — no se agenda en feriado y el acta no se cierra antes del examen
    * def comision = crearComision('2026-01-05T00:00:00', '2026-12-11T00:00:00')

    Given path '/exam-boards'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectId)', termId: '#(comision.termId)', examDate: '2026-07-09T18:00:00' }
    When method post
    Then status 400
    And match response.code == 'EXAM_DATE_ON_HOLIDAY'

    Given path '/exam-boards'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectId)', termId: '#(comision.termId)', examDate: '2099-07-01T18:00:00' }
    When method post
    Then status 201
    * def examBoardId = response.id

    Given path '/exam-boards', examBoardId, 'close'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 409
    And match response.code == 'EXAM_BOARD_NOT_YET_TAKEN'
