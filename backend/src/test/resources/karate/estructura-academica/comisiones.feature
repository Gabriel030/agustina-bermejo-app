Feature: Módulos de curso y comisiones (CourseSubject, CourseSection, SubjectSection)

  # CourseSubjectController (/courses/{id}/subjects, /course-subjects/...),
  # CourseSectionController (/course-subjects/{id}/sections, /course-sections/...),
  # SubjectSectionController (/subjects/{id}/sections, /program-sections/... — nombre
  # heredado, no es un error de esta spec). Alta/edición de comisiones es
  # Preceptor o Administrativo (a diferencia del plan de estudios, exclusivo de
  # Administrativo) — ver plan-de-estudios.feature para Subject/ProgramSubject/ElectiveGroup.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def preceptorToken = fixtures.tokenForRole('PRECEPTOR')
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())
    * def alumnoToken = alumno.accessToken

    * def nombreTerm = 'Ciclo QA ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(nombreTerm)', type: 'ANUAL', startDate: '2026-03-01T00:00:00', endDate: '2026-07-01T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    * def nombreCurso = 'Curso QA ' + java.lang.System.nanoTime()
    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCurso)', termId: '#(termId)', capacity: 20 }
    When method post
    Then status 201
    * def courseId = response.id

  Scenario: Ciclo completo de un módulo de curso — alta, edición, baja y reactivación
    * def nombreModulo = 'Módulo 1 ' + java.lang.System.nanoTime()
    Given path '/courses', courseId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreModulo)', sortOrder: 1 }
    When method post
    Then status 201
    And match response.status == 'ACTIVE'
    * def courseSubjectId = response.id

    Given path '/course-subjects', courseSubjectId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200

    Given path '/course-subjects', courseSubjectId
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Módulo 1 (editado)' }
    When method put
    Then status 200
    And match response.name == 'Módulo 1 (editado)'

    Given path '/course-subjects', courseSubjectId, 'deactivate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/course-subjects', courseSubjectId, 'activate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/courses', courseId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response[*].id contains courseSubjectId

  Scenario: Curso inexistente al agregar módulo — 404
    Given path '/courses', 999999, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Módulo huérfano' }
    When method post
    Then status 404

  Scenario: Módulo de curso inexistente — 404
    Given path '/course-subjects', 999999
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'x' }
    When method put
    Then status 404

  Scenario: Ciclo completo de una comisión de curso — crear, editar, asignar y quitar docente, baja/alta
    * def nombreModulo = 'Módulo con comisión ' + java.lang.System.nanoTime()
    Given path '/courses', courseId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreModulo)' }
    When method post
    Then status 201
    * def courseSubjectId = response.id

    * def nombreTermSeccion = 'Ciclo QA sección ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(nombreTermSeccion)', type: 'ANUAL', startDate: '2026-03-01T00:00:00', endDate: '2026-07-01T00:00:00' }
    When method post
    Then status 201
    * def termSeccionId = response.id

    Given path '/course-subjects', courseSubjectId, 'sections'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { name: 'Comisión A', termId: '#(termSeccionId)', capacity: 25 }
    When method post
    Then status 201
    And match response.status == 'ACTIVE'
    * def sectionId = response.id

    Given path '/course-sections', sectionId
    And header Authorization = 'Bearer ' + adminToken
    And request { capacity: 30 }
    When method put
    Then status 200
    And match response.capacity == 30

    * def instructorId = fixtures.createInstructor()
    Given path '/course-sections', sectionId, 'instructors'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { instructorId: '#(instructorId)', role: 'LEAD' }
    When method post
    Then status 201
    And match response.instructorNombre == '#notnull'
    * def assignmentId = response.id

    Given path '/course-sections', sectionId, 'instructors'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response[*].id contains assignmentId

    Given path '/course-sections', sectionId, 'instructors', assignmentId
    And header Authorization = 'Bearer ' + preceptorToken
    When method delete
    Then status 204

    Given path '/course-sections', sectionId, 'deactivate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/course-sections', sectionId, 'activate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

  Scenario: Módulo de curso o ciclo inexistente al crear comisión — 404
    * def nombreModulo = 'Módulo para 404 ' + java.lang.System.nanoTime()
    Given path '/courses', courseId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreModulo)' }
    When method post
    Then status 201
    * def courseSubjectId = response.id

    Given path '/course-subjects', 999999, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'x', termId: '#(termId)', capacity: 10 }
    When method post
    Then status 404

    Given path '/course-subjects', courseSubjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'x', termId: 999999, capacity: 10 }
    When method post
    Then status 404

  Scenario: Docente ya asignado a la misma comisión — 409
    * def nombreModulo = 'Módulo docente duplicado ' + java.lang.System.nanoTime()
    Given path '/courses', courseId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreModulo)' }
    When method post
    Then status 201
    * def courseSubjectId = response.id

    Given path '/course-subjects', courseSubjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión B', termId: '#(termId)', capacity: 20 }
    When method post
    Then status 201
    * def sectionId = response.id

    * def instructorId = fixtures.createInstructor()
    Given path '/course-sections', sectionId, 'instructors'
    And header Authorization = 'Bearer ' + adminToken
    And request { instructorId: '#(instructorId)', role: 'LEAD' }
    When method post
    Then status 201

    Given path '/course-sections', sectionId, 'instructors'
    And header Authorization = 'Bearer ' + adminToken
    And request { instructorId: '#(instructorId)', role: 'LEAD' }
    When method post
    Then status 409

  Scenario: Quitar una asignación de docente que no pertenece a esa comisión — 404
    * def nombreModulo = 'Módulo dos comisiones ' + java.lang.System.nanoTime()
    Given path '/courses', courseId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreModulo)' }
    When method post
    Then status 201
    * def courseSubjectId = response.id

    Given path '/course-subjects', courseSubjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión C', termId: '#(termId)', capacity: 20 }
    When method post
    Then status 201
    * def sectionCId = response.id

    Given path '/course-subjects', courseSubjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión D', termId: '#(termId)', capacity: 20 }
    When method post
    Then status 201
    * def sectionDId = response.id

    * def instructorId = fixtures.createInstructor()
    Given path '/course-sections', sectionCId, 'instructors'
    And header Authorization = 'Bearer ' + adminToken
    And request { instructorId: '#(instructorId)', role: 'LEAD' }
    When method post
    Then status 201
    * def assignmentId = response.id

    # La asignación existe (en la comisión C), pero se intenta quitar vía la comisión D
    Given path '/course-sections', sectionDId, 'instructors', assignmentId
    And header Authorization = 'Bearer ' + adminToken
    When method delete
    Then status 404

  Scenario: EvaluationScheme inexistente al crear comisión — 404
    * def nombreModulo = 'Módulo con esquema inexistente ' + java.lang.System.nanoTime()
    Given path '/courses', courseId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreModulo)' }
    When method post
    Then status 201
    * def courseSubjectId = response.id

    Given path '/course-subjects', courseSubjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión con esquema inexistente', termId: '#(termId)', capacity: 20, evaluationSchemeId: 999999 }
    When method post
    Then status 404

  Scenario: Alumno no puede crear una comisión — 403
    * def nombreModulo = 'Módulo sin permiso ' + java.lang.System.nanoTime()
    Given path '/courses', courseId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreModulo)' }
    When method post
    Then status 201
    * def courseSubjectId = response.id

    Given path '/course-subjects', courseSubjectId, 'sections'
    And header Authorization = 'Bearer ' + alumnoToken
    And request { name: 'Comisión no autorizada', termId: '#(termId)', capacity: 20 }
    When method post
    Then status 403

  Scenario: Ciclo completo de una comisión de materia del plan de carrera — crear, editar, docente, baja/alta
    * def nombreMateria = 'Materia con comisión ' + java.lang.System.nanoTime()
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateria)' }
    When method post
    Then status 201
    * def subjectId = response.id

    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { name: 'Comisión Única', termId: '#(termId)', capacity: 40 }
    When method post
    Then status 201
    And match response.subjectId == subjectId
    * def subjectSectionId = response.id

    Given path '/program-sections', subjectSectionId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200

    Given path '/program-sections', subjectSectionId
    And header Authorization = 'Bearer ' + adminToken
    And request { capacity: 45 }
    When method put
    Then status 200
    And match response.capacity == 45

    * def instructorId = fixtures.createInstructor()
    Given path '/program-sections', subjectSectionId, 'instructors'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { instructorId: '#(instructorId)', role: 'LEAD' }
    When method post
    Then status 201
    * def assignmentId = response.id

    Given path '/program-sections', subjectSectionId, 'instructors'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response[*].id contains assignmentId

    Given path '/program-sections', subjectSectionId, 'instructors', assignmentId
    And header Authorization = 'Bearer ' + preceptorToken
    When method delete
    Then status 204

    Given path '/program-sections', subjectSectionId, 'deactivate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/program-sections', subjectSectionId, 'activate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

  Scenario: Una comisión de materia compartida por dos carreras queda como una sola fila (decisión de diseño)
    * def nombreMateria = 'Materia compartida ' + java.lang.System.nanoTime()
    * def nombrePrograma1 = 'Carrera QA comparte 1 ' + java.lang.System.nanoTime()
    * def nombrePrograma2 = 'Carrera QA comparte 2 ' + java.lang.System.nanoTime()

    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateria)' }
    When method post
    Then status 201
    * def subjectId = response.id

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

    Given path '/programs', program1Id, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectId)', curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201

    Given path '/programs', program2Id, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectId)', curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201

    # La comisión no referencia ningún Program — se crea una sola vez para la materia
    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión compartida', termId: '#(termId)', capacity: 50 }
    When method post
    Then status 201

    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response == '#[1]'

  Scenario: Quitar una asignación de docente de una comisión de materia que no le corresponde — 404
    * def nombreMateria = 'Materia dos comisiones ' + java.lang.System.nanoTime()
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateria)' }
    When method post
    Then status 201
    * def subjectId = response.id

    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión E', termId: '#(termId)', capacity: 20 }
    When method post
    Then status 201
    * def sectionEId = response.id

    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión F', termId: '#(termId)', capacity: 20 }
    When method post
    Then status 201
    * def sectionFId = response.id

    * def instructorId = fixtures.createInstructor()
    Given path '/program-sections', sectionEId, 'instructors'
    And header Authorization = 'Bearer ' + adminToken
    And request { instructorId: '#(instructorId)', role: 'LEAD' }
    When method post
    Then status 201
    * def assignmentId = response.id

    Given path '/program-sections', sectionFId, 'instructors', assignmentId
    And header Authorization = 'Bearer ' + adminToken
    When method delete
    Then status 404

  Scenario: Subject o Term inexistente al crear comisión de materia — 404
    Given path '/subjects', 999999, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'x', termId: '#(termId)', capacity: 10 }
    When method post
    Then status 404

    * def nombreMateria = 'Materia term inexistente ' + java.lang.System.nanoTime()
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateria)' }
    When method post
    Then status 201
    * def subjectId = response.id

    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'x', termId: 999999, capacity: 10 }
    When method post
    Then status 404
