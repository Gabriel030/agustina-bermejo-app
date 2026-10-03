Feature: Plan de estudios (Subject, ProgramSubject, correlatividades, grupos electivos)

  # SubjectController (/subjects), ProgramSubjectController (/programs/{id}/subjects,
  # /program-subjects/...), ElectiveGroupController (/elective-groups,
  # /programs/{id}/elective-groups). Exclusivo de Administrativo para escritura — a
  # diferencia de facturación no hay lectura habilitada para Director/Preceptor acá,
  # es contenido académico, no financiero ni disciplinar.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def preceptorToken = fixtures.tokenForRole('PRECEPTOR')
    * def directorToken = fixtures.tokenForRole('DIRECTOR')

  Scenario: Ciclo completo de una materia — alta, aprobación, edición, baja y reactivación
    * def nombre = 'Análisis Matemático I ' + java.lang.System.nanoTime()
    * def codigo = Java.type('com.red_escuela.backend.config.TestSubjectCodes').next()
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombre)', code: '#(codigo)', description: 'Materia troncal', weeklyHours: 4, gradeMode: 'NUMERIC' }
    When method post
    Then status 201
    And match response.status == 'DRAFT'
    * def subjectId = response.id

    Given path '/subjects', subjectId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200

    Given path '/subjects', subjectId
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Análisis Matemático I (editada)', description: 'Descripción actualizada' }
    When method put
    Then status 200
    And match response.name == 'Análisis Matemático I (editada)'

    Given path '/subjects', subjectId, 'submit-for-approval'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 200
    And match response.status == 'PENDING_APPROVAL'

    Given path '/subjects', subjectId, 'approve'
    And header Authorization = 'Bearer ' + directorToken
    When method post
    Then status 200
    And match response.status == 'ACTIVE'

    Given path '/subjects', subjectId, 'deactivate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/subjects', subjectId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.status == 'INACTIVE'

    Given path '/subjects', subjectId, 'activate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

  Scenario: Materia con nombre duplicado — 409
    * def nombre = 'Historia ' + java.lang.System.nanoTime()
    * def codigo = Java.type('com.red_escuela.backend.config.TestSubjectCodes').next()
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombre)', code: '#(codigo)' }
    When method post
    Then status 201

    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombre)', code: '#(codigo)' }
    When method post
    Then status 409

  Scenario: Materia inexistente — 404
    Given path '/subjects', 999999
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'x' }
    When method put
    Then status 404

    Given path '/subjects', 999999, 'deactivate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 404

  Scenario: Preceptor no puede crear materias — 403 (exclusivo de Administrativo)
    * def nombre = 'Intento no autorizado ' + java.lang.System.nanoTime()
    Given path '/subjects'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { name: '#(nombre)', code: 'TEST' }
    When method post
    Then status 403

  Scenario: Ciclo completo de plan de estudios — agregar materia a la carrera, editar, correlatividad y baja
    * def nombrePrograma = 'Profesorado QA ' + java.lang.System.nanoTime()
    * def nombreA = 'Materia A ' + java.lang.System.nanoTime()
    * def nombreB = 'Materia B ' + java.lang.System.nanoTime()
    * def codigoA = Java.type('com.red_escuela.backend.config.TestSubjectCodes').next()
    * def codigoB = Java.type('com.red_escuela.backend.config.TestSubjectCodes').next()

    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 4, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreA)', code: '#(codigoA)' }
    When method post
    Then status 201
    * def subjectAId = response.id

    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreB)', code: '#(codigoB)' }
    When method post
    Then status 201
    * def subjectBId = response.id

    # A en el 2do cuatrimestre de 1er año — para que B (1er cuatrimestre, más abajo)
    # pueda ser su correlativa sin violar la validación de orden cronológico (una
    # correlativa tiene que terminar de cursarse antes de que empiece la materia que
    # la requiere, no puede compartir cuatrimestre con ella).
    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subjectAId)', curricularYear: 1, curricularTerm: 'SECOND', credits: 8 }
    When method post
    Then status 201
    * def programSubjectAId = response.id

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subjectBId)', curricularYear: 1, curricularTerm: 'FIRST', credits: 6 }
    When method post
    Then status 201
    * def programSubjectBId = response.id

    Given path '/program-subjects', programSubjectAId
    And header Authorization = 'Bearer ' + adminToken
    And request { credits: 10 }
    When method put
    Then status 200
    And match response.credits == 10

    Given path '/program-subjects', programSubjectAId, 'prerequisites'
    And header Authorization = 'Bearer ' + adminToken
    And request { prerequisiteProgramSubjectId: '#(programSubjectBId)', requirementType: 'APPROVED' }
    When method post
    Then status 201
    * def prerequisiteId = response.id

    Given path '/program-subjects', programSubjectAId, 'prerequisites'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response[0].prerequisiteProgramSubjectId == programSubjectBId

    Given path '/program-subjects/prerequisites', prerequisiteId
    And header Authorization = 'Bearer ' + adminToken
    When method delete
    Then status 204

    Given path '/program-subjects', programSubjectAId, 'deactivate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/program-subjects', programSubjectAId, 'activate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

  Scenario: Agregar la misma materia dos veces a la carrera — 409
    * def nombrePrograma = 'Carrera QA duplicado ' + java.lang.System.nanoTime()
    * def nombreMateria = 'Materia duplicable ' + java.lang.System.nanoTime()
    * def codigo = Java.type('com.red_escuela.backend.config.TestSubjectCodes').next()

    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateria)', code: '#(codigo)' }
    When method post
    Then status 201
    * def subjectId = response.id

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subjectId)', curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subjectId)', curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 409

  Scenario: Carrera o materia inexistente al agregar al plan de estudios — 404
    * def nombrePrograma = 'Carrera QA subject inexistente ' + java.lang.System.nanoTime()

    Given path '/programs', 999999, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: 1, curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 404

    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: 999999, curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 404

  Scenario: Prerequisito de sí misma — 400
    * def nombrePrograma = 'Carrera QA prereq sí misma ' + java.lang.System.nanoTime()
    * def nombreMateria = 'Materia autorreferencial ' + java.lang.System.nanoTime()
    * def codigo = Java.type('com.red_escuela.backend.config.TestSubjectCodes').next()

    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateria)', code: '#(codigo)' }
    When method post
    Then status 201
    * def subjectId = response.id

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subjectId)', curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201
    * def programSubjectId = response.id

    Given path '/program-subjects', programSubjectId, 'prerequisites'
    And header Authorization = 'Bearer ' + adminToken
    And request { prerequisiteProgramSubjectId: '#(programSubjectId)', requirementType: 'APPROVED' }
    When method post
    Then status 400

  Scenario: Prerequisito duplicado — 409, y correlatividad circular — 400
    * def nombrePrograma = 'Carrera QA correlatividades ' + java.lang.System.nanoTime()
    * def nombreC = 'Materia C ' + java.lang.System.nanoTime()
    * def nombreD = 'Materia D ' + java.lang.System.nanoTime()
    * def codigoC = Java.type('com.red_escuela.backend.config.TestSubjectCodes').next()
    * def codigoD = Java.type('com.red_escuela.backend.config.TestSubjectCodes').next()

    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 2, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreC)', code: '#(codigoC)' }
    When method post
    Then status 201
    * def subjectCId = response.id

    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreD)', code: '#(codigoD)' }
    When method post
    Then status 201
    * def subjectDId = response.id

    # C en 2do año y D en 1ro — D tiene que ser cronológicamente anterior a C para
    # poder ser su correlativa (ver validación de orden cronológico agregada a
    # ProgramSubjectService.agregarCorrelatividad).
    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subjectCId)', curricularYear: 2, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201
    * def programSubjectCId = response.id

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subjectDId)', curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201
    * def programSubjectDId = response.id

    # C requiere D — 201
    Given path '/program-subjects', programSubjectCId, 'prerequisites'
    And header Authorization = 'Bearer ' + adminToken
    And request { prerequisiteProgramSubjectId: '#(programSubjectDId)', requirementType: 'APPROVED' }
    When method post
    Then status 201

    # C requiere D de nuevo — 409
    Given path '/program-subjects', programSubjectCId, 'prerequisites'
    And header Authorization = 'Bearer ' + adminToken
    And request { prerequisiteProgramSubjectId: '#(programSubjectDId)', requirementType: 'APPROVED' }
    When method post
    Then status 409

    # D requiere C — cerraría un ciclo (C ya requiere D) — 400
    Given path '/program-subjects', programSubjectDId, 'prerequisites'
    And header Authorization = 'Bearer ' + adminToken
    And request { prerequisiteProgramSubjectId: '#(programSubjectCId)', requirementType: 'APPROVED' }
    When method post
    Then status 400

  Scenario: Correlatividad entre materias de carreras distintas — 400
    * def nombrePrograma1 = 'Carrera QA 1 ' + java.lang.System.nanoTime()
    * def nombrePrograma2 = 'Carrera QA 2 ' + java.lang.System.nanoTime()
    * def nombreSubject1 = 'Materia carrera 1 ' + java.lang.System.nanoTime()
    * def nombreSubject2 = 'Materia carrera 2 ' + java.lang.System.nanoTime()
    * def codigo1 = Java.type('com.red_escuela.backend.config.TestSubjectCodes').next()
    * def codigo2 = Java.type('com.red_escuela.backend.config.TestSubjectCodes').next()

    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma1)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def program1Id = response.id

    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma2)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def program2Id = response.id

    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreSubject1)', code: '#(codigo1)' }
    When method post
    Then status 201
    * def subject1Id = response.id

    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreSubject2)', code: '#(codigo2)' }
    When method post
    Then status 201
    * def subject2Id = response.id

    Given path '/programs', program1Id, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subject1Id)', curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201
    * def programSubject1Id = response.id

    Given path '/programs', program2Id, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subject2Id)', curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201
    * def programSubject2Id = response.id

    Given path '/program-subjects', programSubject1Id, 'prerequisites'
    And header Authorization = 'Bearer ' + adminToken
    And request { prerequisiteProgramSubjectId: '#(programSubject2Id)', requirementType: 'APPROVED' }
    When method post
    Then status 400

  Scenario: Quitar correlatividad inexistente — 404
    Given path '/program-subjects/prerequisites', 999999
    And header Authorization = 'Bearer ' + adminToken
    When method delete
    Then status 404

  Scenario: Grupo electivo — no se puede tagear sin asignar rango a la carrera primero, luego sí; en uso no se puede quitar
    * def nombreGrupo = 'Optativas de Idioma ' + java.lang.System.nanoTime()
    * def nombrePrograma = 'Carrera QA electivas ' + java.lang.System.nanoTime()
    * def nombreMateria = 'Electiva de Idioma ' + java.lang.System.nanoTime()
    * def codigo = Java.type('com.red_escuela.backend.config.TestSubjectCodes').next()

    Given path '/elective-groups'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreGrupo)' }
    When method post
    Then status 201
    * def groupId = response.id

    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateria)', code: '#(codigo)' }
    When method post
    Then status 201
    * def subjectId = response.id

    # Decisión 8: no se puede tagear con un grupo que la carrera no asignó todavía
    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'ELECTIVE', subjectId: '#(subjectId)', curricularYear: 1, curricularTerm: 'FIRST', credits: 5, electiveGroupId: '#(groupId)' }
    When method post
    Then status 400

    Given path '/programs', programId, 'elective-groups'
    And header Authorization = 'Bearer ' + adminToken
    And request { electiveGroupId: '#(groupId)', minRequired: 1, maxAllowed: 2 }
    When method post
    Then status 200

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'ELECTIVE', subjectId: '#(subjectId)', curricularYear: 1, curricularTerm: 'FIRST', credits: 5, electiveGroupId: '#(groupId)' }
    When method post
    Then status 201
    * def programSubjectElectivaId = response.id

    Given path '/programs', programId, 'elective-groups'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response[0].electiveGroupId == groupId

    # Todavía en uso por la materia recién tageada — no se puede quitar. No hay forma de
    # destagear vía API para reintentar: UpdateProgramSubjectRequest.electiveGroupId con JSON
    # null se ignora (editarMateria solo pisa campos no-nulos, ProgramSubjectService.java:121 —
    # es "PATCH parcial", nunca "borrar este campo") — limitación real, no un bug de este test.
    Given path '/programs', programId, 'elective-groups', groupId
    And header Authorization = 'Bearer ' + adminToken
    When method delete
    Then status 409

  Scenario: Quitar un grupo electivo sin uso — 204
    * def nombreGrupo = 'Optativas sin uso ' + java.lang.System.nanoTime()
    * def nombrePrograma = 'Carrera QA sin uso ' + java.lang.System.nanoTime()

    Given path '/elective-groups'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreGrupo)' }
    When method post
    Then status 201
    * def groupId = response.id

    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    Given path '/programs', programId, 'elective-groups'
    And header Authorization = 'Bearer ' + adminToken
    And request { electiveGroupId: '#(groupId)', minRequired: 1, maxAllowed: 2 }
    When method post
    Then status 200

    Given path '/programs', programId, 'elective-groups', groupId
    And header Authorization = 'Bearer ' + adminToken
    When method delete
    Then status 204

  Scenario: Rango inválido de grupo electivo — 400 (minRequired > maxAllowed)
    * def nombreGrupo = 'Grupo rango inválido ' + java.lang.System.nanoTime()
    * def nombrePrograma = 'Carrera QA rango inválido ' + java.lang.System.nanoTime()

    Given path '/elective-groups'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreGrupo)' }
    When method post
    Then status 201
    * def groupId = response.id

    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    Given path '/programs', programId, 'elective-groups'
    And header Authorization = 'Bearer ' + adminToken
    And request { electiveGroupId: '#(groupId)', minRequired: 5, maxAllowed: 1 }
    When method post
    Then status 400

  Scenario: Grupo electivo con nombre duplicado — 409
    * def nombre = 'Optativas duplicadas ' + java.lang.System.nanoTime()
    Given path '/elective-groups'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombre)' }
    When method post
    Then status 201

    Given path '/elective-groups'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombre)' }
    When method post
    Then status 409

  Scenario: Rechazar una materia exige motivo, lo guarda para el administrativo y se limpia al reenviarla
    * def nombre = 'Materia a rechazar ' + java.lang.System.nanoTime()
    * def codigo = java.util.UUID.randomUUID().toString().substring(0, 4)
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombre)', code: '#(codigo)', weeklyHours: 4, gradeMode: 'NUMERIC' }
    When method post
    Then status 201
    * def subjectId = response.id

    Given path '/subjects', subjectId, 'submit-for-approval'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 200

    Given path '/subjects', subjectId, 'reject'
    And header Authorization = 'Bearer ' + directorToken
    And request { reason: '' }
    When method post
    Then status 400

    Given path '/subjects', subjectId, 'reject'
    And header Authorization = 'Bearer ' + directorToken
    And request { reason: 'Falta la bibliografía obligatoria' }
    When method post
    Then status 200
    And match response.status == 'REJECTED'
    And match response.rejectionReason == 'Falta la bibliografía obligatoria'

    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And param status = 'REJECTED'
    When method get
    Then status 200
    * def rechazada = response.filter(function(x){ return x.id == subjectId })
    And match rechazada[0].rejectionReason == 'Falta la bibliografía obligatoria'

    Given path '/subjects', subjectId, 'submit-for-approval'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 200
    And match response.status == 'PENDING_APPROVAL'
    And match response.rejectionReason == null

  Scenario: Una materia sin horas o sin modalidad de calificación se guarda como borrador pero no se puede enviar a aprobación
    * def nombre = 'Materia incompleta ' + java.lang.System.nanoTime()
    * def codigo = java.util.UUID.randomUUID().toString().substring(0, 4)
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombre)', code: '#(codigo)' }
    When method post
    Then status 201
    And match response.status == 'DRAFT'
    * def subjectId = response.id

    Given path '/subjects', subjectId, 'submit-for-approval'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 400
    And match response.code == 'INCOMPLETE_SUBJECT'

    Given path '/subjects', subjectId
    And header Authorization = 'Bearer ' + adminToken
    And request { weeklyHoursLab: 2 }
    When method put
    Then status 200

    Given path '/subjects', subjectId, 'submit-for-approval'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 400
    And match response.code == 'INCOMPLETE_SUBJECT'

    Given path '/subjects', subjectId
    And header Authorization = 'Bearer ' + adminToken
    And request { gradeMode: 'PASS_FAIL' }
    When method put
    Then status 200

    Given path '/subjects', subjectId, 'submit-for-approval'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 200
    And match response.status == 'PENDING_APPROVAL'

  Scenario: El código se corrige en borrador y queda fijo una vez aprobada la materia
    * def nombre = 'Materia con código a corregir ' + java.lang.System.nanoTime()
    * def codigo = java.util.UUID.randomUUID().toString().substring(0, 4)
    * def codigoCorregido = java.util.UUID.randomUUID().toString().substring(0, 4)
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombre)', code: '#(codigo)', weeklyHours: 3, gradeMode: 'NUMERIC' }
    When method post
    Then status 201
    * def subjectId = response.id

    Given path '/subjects', subjectId
    And header Authorization = 'Bearer ' + adminToken
    And request { code: '#(codigoCorregido)' }
    When method put
    Then status 200
    And match response.code == codigoCorregido

    Given path '/subjects', subjectId, 'submit-for-approval'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 200

    Given path '/subjects', subjectId, 'approve'
    And header Authorization = 'Bearer ' + directorToken
    When method post
    Then status 200

    Given path '/subjects', subjectId
    And header Authorization = 'Bearer ' + adminToken
    And request { code: '#(codigo)', weeklyHours: 5 }
    When method put
    Then status 409
    And match response.code == 'SUBJECT_INVALID_TRANSITION'

  Scenario Outline: Materia con horas negativas o intentos de final en cero — 400
    * def nombre = 'Materia inválida ' + java.lang.System.nanoTime()
    * def codigo = java.util.UUID.randomUUID().toString().substring(0, 4)
    * def body = { name: '#(nombre)', code: '#(codigo)', gradeMode: 'NUMERIC' }
    * body.weeklyHours = <weeklyHours>
    * body.weeklyHoursLab = <weeklyHoursLab>
    * body.maxFinalExamAttempts = <attempts>
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request body
    When method post
    Then status 400

    Examples:
      | weeklyHours | weeklyHoursLab | attempts |
      | -5          | 2              | 3        |
      | 4           | -1             | 3        |
      | 4           | 0              | 0        |

  Scenario: Una materia pendiente de aprobación no se puede editar ni agregar a un plan — 409
    * def nombre = 'Materia pendiente ' + java.lang.System.nanoTime()
    * def codigo = java.util.UUID.randomUUID().toString().substring(0, 4)
    * def nombrePrograma = 'Carrera QA pendiente ' + java.lang.System.nanoTime()
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombre)', code: '#(codigo)', weeklyHours: 3, gradeMode: 'NUMERIC' }
    When method post
    Then status 201
    * def subjectId = response.id

    Given path '/subjects', subjectId, 'submit-for-approval'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 200

    Given path '/subjects', subjectId
    And header Authorization = 'Bearer ' + adminToken
    And request { weeklyHours: 6 }
    When method put
    Then status 409

    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 2, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subjectId)', curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 409
    And match response.code == 'SUBJECT_NOT_ASSIGNABLE_TO_PLAN'

  Scenario: Agregar al plan con año fuera de la duración o sin los datos de su categoría — 400 con código
    * def nombrePrograma = 'Carrera QA años ' + java.lang.System.nanoTime()
    * def nombreMateria = 'Materia años ' + java.lang.System.nanoTime()
    * def codigo = java.util.UUID.randomUUID().toString().substring(0, 4)

    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 3, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateria)', code: '#(codigo)' }
    When method post
    Then status 201
    * def subjectId = response.id

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subjectId)', curricularYear: 5, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 400
    And match response.code == 'CURRICULAR_YEAR_OUT_OF_RANGE'

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subjectId)', curricularYear: 1, credits: 5 }
    When method post
    Then status 400
    And match response.code == 'INVALID_PROGRAM_SUBJECT_CATEGORY'

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subjectId)', curricularYear: 3, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201
    * def programSubjectId = response.id

    Given path '/program-subjects', programSubjectId
    And header Authorization = 'Bearer ' + adminToken
    And request { curricularYear: 4 }
    When method put
    Then status 400
    And match response.code == 'CURRICULAR_YEAR_OUT_OF_RANGE'
