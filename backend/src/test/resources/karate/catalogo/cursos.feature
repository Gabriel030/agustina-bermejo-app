Feature: Catálogo — Cursos (CourseController)

  # Mismo criterio que carreras.feature — cubre lo que faltaba de CourseController
  # además de POST (ya usado como setup en Épica 7 / autoservicio).

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def preceptorToken = fixtures.tokenForRole('PRECEPTOR')

    * def descripcionPeriodo = 'Karate - Periodo catálogo curso ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(descripcionPeriodo)', type: 'ANUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    * def nombreCurso = 'Karate - Curso catálogo ' + java.lang.System.nanoTime()
    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCurso)', termId: '#(termId)', schedule: 'Lun 18-21hs', capacity: 15, price: { amount: '20000', currency: 'ARS' }, durationMonths: 2 }
    When method post
    Then status 201
    And match response.status == 'ACTIVE'
    * def courseId = response.id

  Scenario: Editar, desactivar, reactivar, listar filtrado, 403 y 404
    Given path '/courses', courseId
    And header Authorization = 'Bearer ' + adminToken
    And request { capacity: 25 }
    When method put
    Then status 200
    And match response.capacity == 25
    And match response.name == nombreCurso

    # Solo ADMINISTRATIVO puede editar — Preceptor no
    Given path '/courses', courseId
    And header Authorization = 'Bearer ' + preceptorToken
    And request { capacity: 10 }
    When method put
    Then status 403

    Given path '/courses', 999999
    And header Authorization = 'Bearer ' + adminToken
    And request { capacity: 10 }
    When method put
    Then status 404

    Given path '/courses', courseId, 'deactivate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/courses', courseId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.status == 'INACTIVE'

    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And param status = 'INACTIVE'
    When method get
    Then status 200
    * def enInactivos = response.filter(function(x){ return x.id == courseId })
    And match enInactivos == '#[1]'

    Given path '/courses', courseId, 'activate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/courses', courseId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.status == 'ACTIVE'

    Given path '/courses', 999999
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 404

  Scenario: Editar el precio de un curso creado sin precio — 200
    * def nombreSinPrecio = 'Karate - Curso sin precio ' + java.lang.System.nanoTime()
    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreSinPrecio)', termId: '#(termId)', capacity: 10, durationMonths: 3 }
    When method post
    Then status 201
    * def sinPrecioId = response.id

    Given path '/courses', sinPrecioId
    And header Authorization = 'Bearer ' + adminToken
    And request { price: { amount: '18000', currency: 'ARS' } }
    When method put
    Then status 200
    And match response.price == { amount: '18000.00', currency: 'ARS' }

  Scenario Outline: Alta de curso con duración faltante o inválida, o con fechas de inscripción invertidas — 400
    * def nombreInvalido = 'Karate - Curso inválido ' + java.lang.System.nanoTime()
    * def body = { name: '#(nombreInvalido)', termId: '#(termId)', capacity: 10 }
    * if (<duration> != null) body.durationMonths = <duration>
    * if (<start> != null) body.registrationStart = <start>
    * if (<end> != null) body.registrationEnd = <end>
    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request body
    When method post
    Then status 400

    Examples:
      | duration | start                 | end                   |
      | null     | null                  | null                  |
      | -3       | null                  | null                  |
      | 0        | null                  | null                  |
      | 3        | '2027-03-01T00:00:00' | '2027-01-01T00:00:00' |

  Scenario: Editar un curso dejando las fechas de inscripción invertidas o la duración negativa — 400
    Given path '/courses', courseId
    And header Authorization = 'Bearer ' + adminToken
    And request { registrationStart: '2027-02-10T00:00:00', registrationEnd: '2027-01-10T00:00:00' }
    When method put
    Then status 400
    And match response.code == 'INVALID_REGISTRATION_WINDOW'

    Given path '/courses', courseId
    And header Authorization = 'Bearer ' + adminToken
    And request { registrationEnd: '2025-01-01T00:00:00' }
    When method put
    Then status 200

    Given path '/courses', courseId
    And header Authorization = 'Bearer ' + adminToken
    And request { registrationStart: '2025-06-01T00:00:00' }
    When method put
    Then status 400

    Given path '/courses', courseId
    And header Authorization = 'Bearer ' + adminToken
    And request { durationMonths: -1 }
    When method put
    Then status 400

  Scenario: El catálogo público solo lista los cursos publicados, ordenados por nombre
    * def alumnoToken = fixtures.tokenForRole('ALUMNO')
    * def sufijo = '' + java.lang.System.nanoTime()

    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#("Karate catálogo Z " + sufijo)', termId: '#(termId)', capacity: 10, durationMonths: 2 }
    When method post
    Then status 201
    * def lastId = response.id

    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#("Karate catálogo A " + sufijo)', termId: '#(termId)', capacity: 10, durationMonths: 2 }
    When method post
    Then status 201
    * def firstId = response.id

    Given path '/courses', lastId, 'catalog-visibility'
    And header Authorization = 'Bearer ' + adminToken
    And request { visible: true }
    When method put
    Then status 200

    Given path '/courses', firstId, 'catalog-visibility'
    And header Authorization = 'Bearer ' + adminToken
    And request { visible: true }
    When method put
    Then status 200

    Given path '/courses'
    And header Authorization = 'Bearer ' + alumnoToken
    And param status = 'ACTIVE'
    And param visibleInCatalog = true
    When method get
    Then status 200
    * def ids = karate.map(response, function(x){ return x.id })
    And match ids contains firstId
    And match ids contains lastId
    And match ids !contains courseId
    And assert ids.indexOf(firstId) < ids.indexOf(lastId)

  Scenario: El detalle del curso informa si la inscripción está abierta
    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#("Karate curso cerrado " + java.lang.System.nanoTime())', termId: '#(termId)', capacity: 10, durationMonths: 2, registrationStart: '2020-01-01T00:00:00', registrationEnd: '2020-02-01T00:00:00' }
    When method post
    Then status 201
    * def closedCourseId = response.id

    Given path '/courses', closedCourseId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.registrationOpen == false

    Given path '/courses', courseId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.registrationOpen == true

  Scenario: El detalle del curso informa cuando el cupo está completo
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#("Karate curso cupo uno " + java.lang.System.nanoTime())', termId: '#(termId)', capacity: 1, durationMonths: 2 }
    When method post
    Then status 201
    * def fullCourseId = response.id

    Given path '/courses', fullCourseId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#("Módulo " + java.lang.System.nanoTime())' }
    When method post
    Then status 201
    * def courseSubjectId = response.id

    Given path '/course-subjects', courseSubjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión única', termId: '#(termId)', capacity: 1 }
    When method post
    Then status 201

    Given path '/courses', fullCourseId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.full == false

    Given path '/students', alumno.studentId, 'course-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { courseId: '#(fullCourseId)' }
    When method post
    Then status 201
    And match response.status == 'ENROLLED'

    Given path '/courses', fullCourseId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.full == true
