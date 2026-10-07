Feature: Egreso, constancias y excepciones del Director (RF-AL-12/RF-AL-15/RF-DI-03/RF-DI-05)

  # GraduationRequestController/MeGraduationRequestController: flujo de egreso con estados
  # explícitos (decisión 8) — Alumno solicita, Administrativo verifica deuda, Director aprueba.
  # MeCertificateController: constancias en PDF sin entidad propia (decisión 6).
  # DirectorApprovalRequestController: excepciones del Director (decisión 12, catálogo abierto
  # LATE_EXAM/PREREQUISITE_WAIVER).

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def institutionId = fixtures.createInstitution()
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def preceptorToken = fixtures.tokenForRole('PRECEPTOR')
    * def directorToken = fixtures.tokenForRole('DIRECTOR')

    # Carrera sin requisitos (totalCreditsRequired 0, sin ProgramSubject): elegibleParaCompletar
    # da true trivialmente — mismo criterio que persistProgramSinRequisitos en
    # GraduationRequestServiceTest, pero armado 100% vía HTTP para Karate.
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Carrera QA sin requisitos', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programSinRequisitosId = response.id

    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Carrera QA con creditos pendientes', totalYears: 1, totalCreditsRequired: 5 }
    When method post
    Then status 201
    * def programConCreditosId = response.id

    * def subjectCodes = Java.type('com.red_escuela.backend.config.TestSubjectCodes')
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#("Materia QA excepciones " + java.lang.System.nanoTime())', code: '#(subjectCodes.next())' }
    When method post
    Then status 201
    * def subjectId = response.id
    * eval Java.type('com.red_escuela.backend.karate.KarateFixtures').activateSubject(subjectId)

    Given path '/programs', programConCreditosId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subjectId)', curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201

    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#("Materia QA plan completo " + java.lang.System.nanoTime())', code: '#(subjectCodes.next())' }
    When method post
    Then status 201
    * def planSubjectId = response.id
    * eval Java.type('com.red_escuela.backend.karate.KarateFixtures').activateSubject(planSubjectId)

    Given path '/programs', programSinRequisitosId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(planSubjectId)', curricularYear: 1, curricularTerm: 'FIRST', credits: 0 }
    When method post
    Then status 201

  Scenario: Ciclo completo de egreso — solicitar, verificar deuda, aprobar, matriculación queda COMPLETED
    * def alumno = fixtures.createConfirmedStudent(institutionId)
    * def alumnoToken = alumno.accessToken

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programSinRequisitosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id
    * fixtures.completeStudyPlan(programEnrollmentId)

    Given path '/me/program-enrollments', programEnrollmentId, 'graduation-request'
    And header Authorization = 'Bearer ' + alumnoToken
    When method post
    Then status 201
    And match response.status == 'REQUESTED'
    * def graduationRequestId = response.id

    # Antes de aprobar el egreso: sin cursada activa, la constancia regular no es elegible
    Given path '/me/program-enrollments', programEnrollmentId, 'certificates', 'regular'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 400

    # La constancia de materias aprobadas no exige cursada activa — se emite igual (sin materias)
    Given path '/me/program-enrollments', programEnrollmentId, 'certificates', 'approved-subjects'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    And match responseType == 'binary'

    Given path '/graduation-requests', graduationRequestId, 'verify-debt'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 200
    And match response.status == 'ADMIN_VERIFIED'

    Given path '/graduation-requests', graduationRequestId, 'approve'
    And header Authorization = 'Bearer ' + directorToken
    When method post
    Then status 200
    And match response.status == 'APPROVED'

    Given path '/program-enrollments', programEnrollmentId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.status == 'COMPLETED'
    And match response.completionDate == '#notnull'

  Scenario: No elegible para egreso por créditos insuficientes — 400
    * def alumno = fixtures.createConfirmedStudent(institutionId)

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programConCreditosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

    Given path '/me/program-enrollments', programEnrollmentId, 'graduation-request'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method post
    Then status 400

  Scenario: Solicitar egreso dos veces sin que la primera se rechace — 409
    * def alumno = fixtures.createConfirmedStudent(institutionId)

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programSinRequisitosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id
    * fixtures.completeStudyPlan(programEnrollmentId)

    Given path '/me/program-enrollments', programEnrollmentId, 'graduation-request'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method post
    Then status 201

    Given path '/me/program-enrollments', programEnrollmentId, 'graduation-request'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method post
    Then status 409

  Scenario: Verificar deuda con una cuota vencida — el trámite sigue en REQUESTED (decisión 10), 409
    * def alumno = fixtures.createConfirmedStudent(institutionId)

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programSinRequisitosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id
    * fixtures.completeStudyPlan(programEnrollmentId)

    Given path '/me/program-enrollments', programEnrollmentId, 'graduation-request'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method post
    Then status 201
    * def graduationRequestId = response.id

    * fixtures.createPendingInvoice(programEnrollmentId, '2026-01-10')

    Given path '/graduation-requests', graduationRequestId, 'verify-debt'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 409
    And match response.code == 'PENDING_DEBT'

    Given path '/program-enrollments', programEnrollmentId, 'graduation-requests'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response[0].status == 'REQUESTED'

  Scenario: Verificar deuda con una cuota impaga que todavía no venció — también bloquea, 409
    * def alumno = fixtures.createConfirmedStudent(institutionId)

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programSinRequisitosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id
    * fixtures.completeStudyPlan(programEnrollmentId)

    Given path '/me/program-enrollments', programEnrollmentId, 'graduation-request'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method post
    Then status 201
    * def graduationRequestId = response.id

    * fixtures.createPendingInvoice(programEnrollmentId, '2099-12-31')

    Given path '/graduation-requests', graduationRequestId, 'verify-debt'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 409
    And match response.code == 'PENDING_DEBT'

  Scenario: Aprobar el egreso de un alumno que dejó de cumplir los requisitos — 409 con código
    * def alumno = fixtures.createConfirmedStudent(institutionId)

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programSinRequisitosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id
    * fixtures.completeStudyPlan(programEnrollmentId)

    Given path '/me/program-enrollments', programEnrollmentId, 'graduation-request'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method post
    Then status 201
    * def graduationRequestId = response.id

    Given path '/graduation-requests', graduationRequestId, 'verify-debt'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 200

    Given path '/programs', programSinRequisitosId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subjectId)', curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201

    Given path '/graduation-requests', graduationRequestId, 'approve'
    And header Authorization = 'Bearer ' + directorToken
    When method post
    Then status 409
    And match response.code == 'GRADUATION_REQUIREMENTS_NOT_MET'

    Given path '/program-enrollments', programEnrollmentId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.status == 'ENROLLED'

  Scenario: Aprobar el egreso de un alumno con una cuota vencida — 409 con código
    * def alumno = fixtures.createConfirmedStudent(institutionId)

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programSinRequisitosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id
    * fixtures.completeStudyPlan(programEnrollmentId)

    Given path '/me/program-enrollments', programEnrollmentId, 'graduation-request'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method post
    Then status 201
    * def graduationRequestId = response.id

    Given path '/graduation-requests', graduationRequestId, 'verify-debt'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 200

    * fixtures.createPendingInvoice(programEnrollmentId, '2026-01-10')

    Given path '/graduation-requests', graduationRequestId, 'approve'
    And header Authorization = 'Bearer ' + directorToken
    When method post
    Then status 409
    And match response.code == 'PENDING_DEBT'

  Scenario: Motivo de rechazo de egreso de más de 500 caracteres — 400, no 409
    * def alumno = fixtures.createConfirmedStudent(institutionId)
    * def motivoLargo = karate.repeat(501, function(){ return 'a' }).join('')

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programSinRequisitosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id
    * fixtures.completeStudyPlan(programEnrollmentId)

    Given path '/me/program-enrollments', programEnrollmentId, 'graduation-request'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method post
    Then status 201
    * def graduationRequestId = response.id

    Given path '/graduation-requests', graduationRequestId, 'reject'
    And header Authorization = 'Bearer ' + adminToken
    And request { motivo: '#(motivoLargo)' }
    When method post
    Then status 400

  Scenario: Rechazar un trámite permite volver a solicitar
    * def alumno = fixtures.createConfirmedStudent(institutionId)

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programSinRequisitosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id
    * fixtures.completeStudyPlan(programEnrollmentId)

    Given path '/me/program-enrollments', programEnrollmentId, 'graduation-request'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method post
    Then status 201
    * def graduationRequestId = response.id

    Given path '/graduation-requests', graduationRequestId, 'reject'
    And header Authorization = 'Bearer ' + adminToken
    And request { motivo: 'Documentación incompleta' }
    When method post
    Then status 200
    And match response.status == 'REJECTED'

    Given path '/me/program-enrollments', programEnrollmentId, 'graduation-request'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method post
    Then status 201

  Scenario: Preceptor no puede aprobar un egreso — 403 (solo Director)
    * def alumno = fixtures.createConfirmedStudent(institutionId)

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programSinRequisitosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id
    * fixtures.completeStudyPlan(programEnrollmentId)

    Given path '/me/program-enrollments', programEnrollmentId, 'graduation-request'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method post
    Then status 201
    * def graduationRequestId = response.id

    Given path '/graduation-requests', graduationRequestId, 'verify-debt'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 200

    Given path '/graduation-requests', graduationRequestId, 'approve'
    And header Authorization = 'Bearer ' + preceptorToken
    When method post
    Then status 403

  Scenario: Trámite de egreso inexistente — 404
    Given path '/graduation-requests', 999999, 'verify-debt'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 404

  Scenario: Constancia de una matriculación ajena — 404, no 403 (mismo criterio que el resto de autoservicio)
    * def alumno = fixtures.createConfirmedStudent(institutionId)
    * def otroAlumno = fixtures.createConfirmedStudent(institutionId)

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programSinRequisitosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

    Given path '/me/program-enrollments', programEnrollmentId, 'certificates', 'approved-subjects'
    And header Authorization = 'Bearer ' + otroAlumno.accessToken
    When method get
    Then status 404

  Scenario: Ciclo completo de excepción del Director — solicitar, aprobar
    * def alumno = fixtures.createConfirmedStudent(institutionId)

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programConCreditosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

    Given path '/director-approval-requests'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { type: 'LATE_EXAM', programEnrollmentId: '#(programEnrollmentId)', subjectId: '#(subjectId)', justification: 'Alumno con certificado médico' }
    When method post
    Then status 201
    And match response.status == 'REQUESTED'
    * def exceptionId = response.id

    Given path '/director-approval-requests'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    And match response[*].id contains exceptionId

    Given path '/director-approval-requests', exceptionId, 'approve'
    And header Authorization = 'Bearer ' + directorToken
    And request { notas: 'Aprobado, presentó certificado' }
    When method post
    Then status 200
    And match response.status == 'APPROVED'

    Given path '/director-approval-requests'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    And match response[*].id !contains exceptionId

  Scenario: Alumno no puede solicitar una excepción del Director — 403
    * def alumno = fixtures.createConfirmedStudent(institutionId)

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programSinRequisitosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

    Given path '/director-approval-requests'
    And header Authorization = 'Bearer ' + alumno.accessToken
    And request { type: 'PREREQUISITE_WAIVER', programEnrollmentId: '#(programEnrollmentId)', justification: 'Intento no autorizado' }
    When method post
    Then status 403

  Scenario: Rechazar una excepción, y decidir de nuevo ya no es posible — 409
    * def alumno = fixtures.createConfirmedStudent(institutionId)

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programConCreditosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

    Given path '/director-approval-requests'
    And header Authorization = 'Bearer ' + adminToken
    And request { type: 'LATE_EXAM', programEnrollmentId: '#(programEnrollmentId)', subjectId: '#(subjectId)', justification: 'Solicitud a rechazar' }
    When method post
    Then status 201
    * def exceptionId = response.id

    Given path '/director-approval-requests', exceptionId, 'reject'
    And header Authorization = 'Bearer ' + directorToken
    And request { notas: 'No corresponde' }
    When method post
    Then status 200
    And match response.status == 'REJECTED'

    Given path '/director-approval-requests', exceptionId, 'approve'
    And header Authorization = 'Bearer ' + directorToken
    When method post
    Then status 409
    And match response.code == 'INVALID_DIRECTOR_APPROVAL_REQUEST_STATE'

  Scenario: Excepción sin materia — 400 con código
    * def alumno = fixtures.createConfirmedStudent(institutionId)

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programConCreditosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

    Given path '/director-approval-requests'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { type: 'PREREQUISITE_WAIVER', programEnrollmentId: '#(programEnrollmentId)', justification: 'Sin materia' }
    When method post
    Then status 400
    And match response.code == 'DIRECTOR_APPROVAL_SUBJECT_REQUIRED'

  Scenario: Excepción con una materia que no es del plan de la carrera — 400 con código
    * def alumno = fixtures.createConfirmedStudent(institutionId)

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programSinRequisitosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

    Given path '/director-approval-requests'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { type: 'LATE_EXAM', programEnrollmentId: '#(programEnrollmentId)', subjectId: '#(subjectId)', justification: 'Materia de otra carrera' }
    When method post
    Then status 400
    And match response.code == 'SUBJECT_NOT_IN_PROGRAM_PLAN'

  Scenario: Justificación de más de 1000 caracteres — 400, no 409
    * def alumno = fixtures.createConfirmedStudent(institutionId)
    * def justificacionLarga = karate.repeat(1001, function(){ return 'a' }).join('')

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programConCreditosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

    Given path '/director-approval-requests'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { type: 'LATE_EXAM', programEnrollmentId: '#(programEnrollmentId)', subjectId: '#(subjectId)', justification: '#(justificacionLarga)' }
    When method post
    Then status 400

  Scenario: Notas de la decisión de más de 500 caracteres — 400, no 409
    * def alumno = fixtures.createConfirmedStudent(institutionId)
    * def notasLargas = karate.repeat(501, function(){ return 'a' }).join('')

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: #(programConCreditosId) }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

    Given path '/director-approval-requests'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { type: 'LATE_EXAM', programEnrollmentId: '#(programEnrollmentId)', subjectId: '#(subjectId)', justification: 'Notas largas' }
    When method post
    Then status 201
    * def exceptionId = response.id

    Given path '/director-approval-requests', exceptionId, 'reject'
    And header Authorization = 'Bearer ' + directorToken
    And request { notas: '#(notasLargas)' }
    When method post
    Then status 400

  Scenario: Excepción sobre una matriculación inexistente — 404
    Given path '/director-approval-requests'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { type: 'LATE_EXAM', programEnrollmentId: 999999, justification: 'Matriculación inexistente' }
    When method post
    Then status 404
