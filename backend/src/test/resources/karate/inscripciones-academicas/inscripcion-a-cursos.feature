Feature: Inscripción a cursos (CourseEnrollmentController, CourseSectionEnrollmentController)

  # Decisión 15: el alta a un curso inscribe automáticamente al alumno en TODOS los
  # módulos ACTIVE del curso, "todo o nada" en la misma transacción — no hay alta
  # manual de CourseSubjectSectionEnrollment, solo baja puntual y consulta
  # (CourseSectionEnrollmentController). A diferencia de ProgramEnrollment, la baja
  # del CourseEnrollment SÍ cascadea a todas sus inscripciones de módulo.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')

    * def nombreTerm = 'Ciclo QA cursos ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(nombreTerm)', type: 'ANUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

  Scenario: Ciclo completo — inscribirse a un curso de un solo módulo, ver progreso, dar de baja con cascada
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    * def nombreCurso = 'Curso QA simple ' + java.lang.System.nanoTime()
    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCurso)', termId: '#(termId)', capacity: 20, durationMonths: 3 }
    When method post
    Then status 201
    * def courseId = response.id

    * def nombreModulo = 'Módulo único ' + java.lang.System.nanoTime()
    Given path '/courses', courseId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreModulo)' }
    When method post
    Then status 201
    * def courseSubjectId = response.id

    Given path '/course-subjects', courseSubjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión única', termId: '#(termId)', capacity: 20, durationMonths: 3 }
    When method post
    Then status 201
    * def sectionId = response.id

    Given path '/students', alumno.studentId, 'course-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { courseId: '#(courseId)' }
    When method post
    Then status 201
    And match response.status == 'ENROLLED'
    * def courseEnrollmentId = response.id

    Given path '/course-enrollments', courseEnrollmentId, 'progress'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200

    Given path '/course-enrollments', courseEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response == '#[1]'
    And match response[0].sectionId == sectionId
    * def sectionEnrollmentId = response[0].id

    Given path '/course-enrollments', courseEnrollmentId, 'withdraw'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/course-enrollments', courseEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response[0].status != 'ENROLLED'

  Scenario: Inscribirse dos veces al mismo curso — 409
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    * def nombreCurso = 'Curso QA doble inscripcion ' + java.lang.System.nanoTime()
    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCurso)', termId: '#(termId)', capacity: 20, durationMonths: 3 }
    When method post
    Then status 201
    * def courseId = response.id

    Given path '/students', alumno.studentId, 'course-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { courseId: '#(courseId)' }
    When method post
    Then status 201

    Given path '/students', alumno.studentId, 'course-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { courseId: '#(courseId)' }
    When method post
    Then status 409

  Scenario: Módulo sin ninguna comisión configurada — 409 (todo o nada)
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    * def nombreCurso = 'Curso QA sin comision ' + java.lang.System.nanoTime()
    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCurso)', termId: '#(termId)', capacity: 20, durationMonths: 3 }
    When method post
    Then status 201
    * def courseId = response.id

    * def nombreModulo = 'Módulo huérfano ' + java.lang.System.nanoTime()
    Given path '/courses', courseId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreModulo)' }
    When method post
    Then status 201

    Given path '/students', alumno.studentId, 'course-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { courseId: '#(courseId)' }
    When method post
    Then status 409

  Scenario: Módulo con dos comisiones — ambigua sin override (400), resuelta con sectionOverrides (201)
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    * def nombreCurso = 'Curso QA dos comisiones ' + java.lang.System.nanoTime()
    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCurso)', termId: '#(termId)', capacity: 20, durationMonths: 3 }
    When method post
    Then status 201
    * def courseId = response.id

    * def nombreModulo = 'Módulo ambiguo ' + java.lang.System.nanoTime()
    Given path '/courses', courseId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreModulo)' }
    When method post
    Then status 201
    * def courseSubjectId = response.id

    Given path '/course-subjects', courseSubjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión mañana', termId: '#(termId)', capacity: 20, durationMonths: 3 }
    When method post
    Then status 201
    * def sectionManianaId = response.id

    Given path '/course-subjects', courseSubjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión tarde', termId: '#(termId)', capacity: 20, durationMonths: 3 }
    When method post
    Then status 201

    Given path '/students', alumno.studentId, 'course-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { courseId: '#(courseId)' }
    When method post
    Then status 400

    * def overrides = {}
    * eval overrides[courseSubjectId + ''] = sectionManianaId

    Given path '/students', alumno.studentId, 'course-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { courseId: '#(courseId)', sectionOverrides: '#(overrides)' }
    When method post
    Then status 201

  Scenario: Ventana de inscripción cerrada — 400
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    * def nombreCurso = 'Curso QA ventana cerrada ' + java.lang.System.nanoTime()
    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCurso)', termId: '#(termId)', capacity: 20, durationMonths: 3, registrationStart: '2020-01-01T00:00:00', registrationEnd: '2020-02-01T00:00:00' }
    When method post
    Then status 201
    * def courseId = response.id

    Given path '/students', alumno.studentId, 'course-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { courseId: '#(courseId)' }
    When method post
    Then status 400

  Scenario: Curso inexistente — 404, y dar de baja una inscripción inexistente — 404
    Given path '/students', 999999, 'course-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { courseId: 999999 }
    When method post
    Then status 404

    Given path '/course-enrollments', 999999, 'withdraw'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 404

  Scenario: Dar de baja dos veces la misma inscripción a un curso — 409
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    * def nombreCurso = 'Curso QA doble baja ' + java.lang.System.nanoTime()
    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCurso)', termId: '#(termId)', capacity: 20, durationMonths: 3 }
    When method post
    Then status 201
    * def courseId = response.id

    Given path '/students', alumno.studentId, 'course-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { courseId: '#(courseId)' }
    When method post
    Then status 201
    * def courseEnrollmentId = response.id

    Given path '/course-enrollments', courseEnrollmentId, 'withdraw'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/course-enrollments', courseEnrollmentId, 'withdraw'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 409

  Scenario: Baja puntual de un módulo vía CourseSectionEnrollmentController — 204, y de nuevo 409
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    * def nombreCurso = 'Curso QA baja puntual ' + java.lang.System.nanoTime()
    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCurso)', termId: '#(termId)', capacity: 20, durationMonths: 3 }
    When method post
    Then status 201
    * def courseId = response.id

    * def nombreModulo = 'Módulo baja puntual ' + java.lang.System.nanoTime()
    Given path '/courses', courseId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreModulo)' }
    When method post
    Then status 201
    * def courseSubjectId = response.id

    Given path '/course-subjects', courseSubjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión', termId: '#(termId)', capacity: 20, durationMonths: 3 }
    When method post
    Then status 201

    Given path '/students', alumno.studentId, 'course-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { courseId: '#(courseId)' }
    When method post
    Then status 201
    * def courseEnrollmentId = response.id

    Given path '/course-enrollments', courseEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    * def sectionEnrollmentId = response[0].id

    Given path '/course-section-enrollments', sectionEnrollmentId, 'withdraw'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/course-section-enrollments', sectionEnrollmentId, 'withdraw'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 409

  Scenario: Inscripción de módulo inexistente — 404
    Given path '/course-section-enrollments', 999999, 'withdraw'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 404
